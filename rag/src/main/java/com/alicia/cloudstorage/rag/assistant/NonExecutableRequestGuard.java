package com.alicia.cloudstorage.rag.assistant;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Separates language about an operation from a request to execute that operation.
 * These guards run before model/local intent arbitration so a high-confidence verb
 * match cannot turn negation, explanation, ambiguity, or cancellation into a plan.
 */
final class NonExecutableRequestGuard {

    private static final Pattern MUTATION = Pattern.compile(
            "删除|删掉|删了|移除|清理|回收站|移动|移到|搬到|挪到|归档到|重命名|改名|分享|上传|新建|创建",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern NEGATED_MUTATION = Pattern.compile(
            "(?:^|[，,。；;])(?:先)?(?:不要|别|不用|无需|不必)(?:再)?[^，,。；;]{0,16}(?:删除|删掉|删了|移除|清理|移动|移到|搬到|挪到|归档到|重命名|改名|分享|上传|新建|创建)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern HYPOTHETICAL_LEAD = Pattern.compile(
            "^(?:如果|假如|假设|要是)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern QUESTION_END = Pattern.compile(
            "(?:会怎样|会发生什么|会不会|什么后果|什么影响|怎么样|如何|吗|呢|[？?])(?:[。！!]*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern QUOTED_OPERATION = Pattern.compile(
            "^[\\s]*[“\"'‘].*(?:删除|删掉|移动|重命名|改名|分享|上传|新建|创建).*[”\"'’].*(?:这句话|什么意思|是什么意思|含义|怎么理解)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern ALTERNATIVE_CONNECTOR = Pattern.compile(
            "或者|或是|还是|二选一",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern UNRESOLVED_REFERENCE = Pattern.compile(
            "^(?:请|麻烦)?(?:帮我)?(?:把)?(?:它|这个|那个)(?:文件|文件夹|目录)?(?:给我)?(?:删除|删掉|删了|移除|移动|移到|搬到|挪到|重命名|改名|分享)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CANCELLATION = Pattern.compile(
            "^(?:算了(?:[，,]?(?:不用了|不做了))?|不用了|不做了|先不做了|取消|停止|停下|撤销|作罢)[。！!]*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final List<Pattern> DISTINCT_OPERATIONS = List.of(
            Pattern.compile("删除|删掉|删了|移除|清理|回收站"),
            Pattern.compile("移动|移到|搬到|挪到|归档到|放到|放进"),
            Pattern.compile("重命名|改名"),
            Pattern.compile("分享"),
            Pattern.compile("上传"),
            Pattern.compile("新建|创建")
    );

    Optional<Decision> evaluate(String message, AssistantConversationState conversation) {
        String value = message == null ? "" : message.trim();
        if (value.isBlank()) {
            return Optional.empty();
        }
        if (CANCELLATION.matcher(value).matches() && hasPendingOperation(conversation)) {
            return Optional.of(new Decision(
                    "cancel_pending_operation",
                    "assistant_acknowledgement",
                    "CANCELLATION",
                    "RESPOND",
                    "好的，这次操作已取消，我不会继续提交文件变更。"
            ));
        }
        if (QUOTED_OPERATION.matcher(value).find()) {
            return Optional.of(new Decision(
                    "quoted_operation_explanation",
                    "assistant_writing_help",
                    "NEW_TASK",
                    "RESPOND",
                    "这句话是在描述一个文件操作；这里只解释它的含义，不会执行其中的操作。"
            ));
        }
        if (!value.matches(".*(?:不要忘记|别忘了).*")
                && NEGATED_MUTATION.matcher(value).find()) {
            return Optional.of(new Decision(
                    "negated_operation",
                    "assistant_acknowledgement",
                    "NEW_TASK",
                    "RESPOND",
                    "好的，我不会执行这项文件操作。若要改做其他操作，请重新说明。"
            ));
        }
        if (HYPOTHETICAL_LEAD.matcher(value).find()
                && MUTATION.matcher(value).find()
                && QUESTION_END.matcher(value).find()) {
            return Optional.of(new Decision(
                    "hypothetical_operation",
                    "assistant_capability_examples",
                    "NEW_TASK",
                    "RESPOND",
                    "这是一个假设性问题，我不会执行文件操作。实际变更只会在目标明确并经过你确认后提交。"
            ));
        }
        if (ALTERNATIVE_CONNECTOR.matcher(value).find() && distinctOperationCount(value) > 1) {
            return Optional.of(new Decision(
                    "conflicting_operations",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "这句话包含多个互斥操作。请明确选择其中一个，我不会先执行任何操作。"
            ));
        }
        if (UNRESOLVED_REFERENCE.matcher(value).find() && !hasCandidateContext(conversation)) {
            return Optional.of(new Decision(
                    "unresolved_reference",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "我还不知道“它”指哪个文件或文件夹。请提供名称，或先从候选中选择目标。"
            ));
        }
        return Optional.empty();
    }

    private int distinctOperationCount(String message) {
        return (int) DISTINCT_OPERATIONS.stream()
                .filter(pattern -> pattern.matcher(message).find())
                .count();
    }

    private boolean hasCandidateContext(AssistantConversationState conversation) {
        return conversation != null
                && conversation.focus() != null
                && conversation.focus().hasCandidateContext();
    }

    private boolean hasPendingOperation(AssistantConversationState conversation) {
        if (conversation == null) {
            return false;
        }
        ActionDraft draft = conversation.pendingActionDraft();
        if (draft != null && draft.type() != null
                && !draft.type().isBlank()
                && !List.of("none", "search").contains(draft.type())) {
            return true;
        }
        return conversation.hasPendingSlots()
                && conversation.pendingIntentId() != null
                && !conversation.pendingIntentId().isBlank();
    }

    record Decision(
            String id,
            String intentId,
            String relation,
            String operation,
            String assistantText
    ) {
    }
}
