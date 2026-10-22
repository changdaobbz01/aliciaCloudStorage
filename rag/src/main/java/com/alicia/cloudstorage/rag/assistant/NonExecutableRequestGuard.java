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
            "删除|删掉|删了|删|移除|清理|回收站|移动|移到|搬到|挪到|归档到|重命名|改名|分享|上传|新建|创建|"
                    + "delete|remove|move|rename|share|upload|create",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern NEGATED_MUTATION = Pattern.compile(
            "(?:^|[，,。；;])(?:先)?(?:不要|别|不用|无需|不必)(?:再)?[^，,。；;]{0,16}(?:删除|删(?:掉|了)?|移除|清理|移动|移到|移去|搬到|搬去|挪到|挪去|归档到|重命名|改名|分享|上传|新建|创建)",
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
            "[“\"'‘].*(?:删除|删掉|移动|重命名|改名|分享|上传|新建|创建|delete|remove|move|rename|share|upload|create).*"
                    + "[”\"'’].*(?:这句话|什么意思|是什么意思|含义|怎么理解|改写|重写|润色|换个说法)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern QUOTED_OPERATION_RISK_REVIEW = Pattern.compile(
            "[“\"'‘].*(?:删除|删掉|清理|移除|移动|重命名|改名|分享|上传|新建|创建|delete|remove|move|rename|share|upload|create).*"
                    + "[”\"'’].*(?:风险|是否安全|安全性|危险|能不能执行|可否执行)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern OPERATION_PROCESS_QUESTION = Pattern.compile(
            "(?=.*(?:了解|想知道|请问|询问|说明|介绍))"
                    + "(?=.*(?:删除|删掉|清理|移除|移动|重命名|改名|分享|上传|新建|创建))"
                    + "(?=.*(?:确认.{0,4}(?:流程|步骤|规则|机制|方式)|(?:流程|步骤|规则|机制|方式).{0,4}确认)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern META_OPERATION_DISCLAIMER = Pattern.compile(
            "(?:只是|仅仅是|仅是|只是个|只是一个).{0,12}(?:举例|例子|示例)|"
                    + "(?:不要|别|无需|不必).{0,12}(?:真的)?(?:执行|操作)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern STRUCTURED_TEXT_EXPLANATION = Pattern.compile(
            "^(?:请)?(?:解释|说明|分析|解读|帮我看(?:看)?).{0,20}(?:JSON|代码|文本|配置|指令).*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CONTRADICTORY_PRESERVATION = Pattern.compile(
            "(?:删除|删掉|移除|清理).{0,24}(?:保留|不要动|别动|不改|不能动)|"
                    + "(?:保留|不要动|别动|不改|不能动).{0,24}(?:删除|删掉|移除|清理)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern SAFETY_BYPASS = Pattern.compile(
            "(?:忽略|绕过|跳过).{0,16}(?:安全|规则|确认|权限)|"
                    + "(?:不要|无需|不必|跳过).{0,8}确认|"
                    + "(?:直接执行|直接操作).{0,16}(?:告诉我|声称).{0,8}(?:完成|成功)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern QUOTED_NAME_SEARCH = Pattern.compile(
            "(?:找|查找|搜索|检索).*?(?:名字|名称|文件名).*?(?:包含|含有|含|带有|带).*?"
                    + "([“\"'‘][^”\"'’]+[”\"'’])",
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
    private static final Pattern MUTATION_REFERENCE = Pattern.compile(
            "(?:它|这个|那个)(?:文件夹|目录|文件)?(?=(?:重命名|改名|移动|搬|挪|到|至|进|$))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern RENAME_WITH_NEW_NAME = Pattern.compile(
            "(?:重命名为|重命名成|改名为|改名成|改成|改为)\\s*([^，,。；;!?！？]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MOVE_DESTINATION = Pattern.compile(
            "(?:到|至|进)\\s*([^，,。；;!?！？]+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CANCELLATION = Pattern.compile(
            "^(?:(?:算了[，,]?)?(?:不用了|不做了|先不做了|取消(?:这个|该|刚才的)?(?:操作|任务|动作)?|停止|停下|撤销|作罢)|算了)[。！!]*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CONTEXTLESS_CONFIRMATION = Pattern.compile(
            "^(?:请)?(?:确认(?:并|后)?执行|确认执行|确认|执行|就(?:这样|这么)执行)(?:吧|了)?[。！!]*$",
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
        if (CONTEXTLESS_CONFIRMATION.matcher(value).matches() && !hasPendingOperation(conversation)) {
            return Optional.of(new Decision(
                    "confirmation_without_pending_operation",
                    "fallback",
                    "CONFIRMATION",
                    "UNKNOWN",
                    "当前没有等待确认的文件操作。请先说明要执行的操作和目标。"
            ));
        }
        String guardedValue = value;
        var quotedName = QUOTED_NAME_SEARCH.matcher(value);
        if (quotedName.find()) {
            guardedValue = value.substring(0, quotedName.start(1))
                    + "“文件名”"
                    + value.substring(quotedName.end(1));
        }
        if (QUOTED_OPERATION_RISK_REVIEW.matcher(guardedValue).find()) {
            return Optional.of(new Decision(
                    "quoted_operation_risk_review",
                    "assistant_capability_examples",
                    "NEW_TASK",
                    "RESPOND",
                    "这是一条需要安全审查的文件操作描述，我只会说明风险和确认要求，不会执行其中的操作。"
            ));
        }
        if (QUOTED_OPERATION.matcher(guardedValue).find()) {
            return Optional.of(new Decision(
                    "quoted_operation_explanation",
                    "assistant_writing_help",
                    "NEW_TASK",
                    "RESPOND",
                    "这句话是在描述一个文件操作；这里只解释它的含义，不会执行其中的操作。"
            ));
        }
        if (MUTATION.matcher(guardedValue).find() && STRUCTURED_TEXT_EXPLANATION.matcher(guardedValue).matches()) {
            return Optional.of(new Decision(
                    "structured_operation_explanation",
                    "assistant_writing_help",
                    "NEW_TASK",
                    "RESPOND",
                    "这段内容只会作为文本进行解释，不会把其中的字段或命令当成文件操作。"
            ));
        }
        if (OPERATION_PROCESS_QUESTION.matcher(guardedValue).matches()) {
            return Optional.of(new Decision(
                    "operation_process_question",
                    "assistant_capability_examples",
                    "NEW_TASK",
                    "RESPOND",
                    "我可以说明文件操作的确认流程和安全规则；这次只做说明，不会提交任何变更。"
            ));
        }
        if (MUTATION.matcher(guardedValue).find() && META_OPERATION_DISCLAIMER.matcher(guardedValue).find()) {
            return Optional.of(new Decision(
                    "operation_disclaimer",
                    "assistant_acknowledgement",
                    "NEW_TASK",
                    "RESPOND",
                    "明白，这只是示例或说明，我不会执行其中的文件操作。"
            ));
        }
        if (MUTATION.matcher(guardedValue).find() && SAFETY_BYPASS.matcher(guardedValue).find()) {
            return Optional.of(new Decision(
                    "safety_bypass_request",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "我不会绕过确认、安全规则或权限执行文件操作。请按正常流程明确目标并在预览后确认。"
            ));
        }
        if (CONTRADICTORY_PRESERVATION.matcher(guardedValue).find()) {
            return Optional.of(new Decision(
                    "contradictory_preservation",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "这句话同时要求删除和保留同一对象。请明确选择一种结果，我不会先执行任何操作。"
            ));
        }
        if (!value.matches(".*(?:不要忘记|别忘了).*")
                && NEGATED_MUTATION.matcher(guardedValue).find()) {
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
        if (!hasCandidateContext(conversation) && hasResolvableMutationReference(value)) {
            return Optional.of(new Decision(
                    "unresolved_rebindable_reference",
                    "fallback",
                    "NEW_TASK",
                    "UNKNOWN",
                    "我会保留这次移动或重命名意图，但需要先确定“这个文件”具体指哪一个。"
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

    private boolean hasResolvableMutationReference(String message) {
        if (!MUTATION_REFERENCE.matcher(message).find()) {
            return false;
        }
        if (RENAME_WITH_NEW_NAME.matcher(message).find()) {
            return true;
        }
        if (!TextSupport.containsAny(message, List.of("移动", "移到", "搬到", "挪到", "搬", "挪"))) {
            return false;
        }
        var destination = MOVE_DESTINATION.matcher(message);
        if (!destination.find()) {
            return false;
        }
        String value = destination.group(1).trim();
        return !List.of("那里", "这里", "那儿", "这儿", "那个", "这个", "某处", "一个地方", "目标目录", "文件夹", "目录")
                .contains(value);
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
        boolean requiresSemanticResolution() {
            return "unresolved_rebindable_reference".equals(id);
        }
    }
}
