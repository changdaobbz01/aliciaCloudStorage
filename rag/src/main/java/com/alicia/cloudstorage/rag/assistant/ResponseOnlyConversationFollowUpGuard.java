package com.alicia.cloudstorage.rag.assistant;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves short contextual follow-ups only for response-only conversation intents.
 * It never creates an action draft and deliberately yields to explicit file commands.
 */
final class ResponseOnlyConversationFollowUpGuard {

    private static final Pattern FILE_OPERATION_REQUEST = Pattern.compile(
            "(?:删除|删掉|移除|清理|清空|移动|移到|搬到|挪到|归档到|重命名|改名|分享|上传|新建|创建|"
                    + "搜索|查找|检索|列出|打开|进入|delete|remove|move|rename|share|upload|create|search|find)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern EXECUTION_CONFIRMATION = Pattern.compile(
            "^(?:好|好的|行|可以)[，,]?(?:那)?(?:就)?(?:这么|这样|照此)?执行(?:吧)?[。！!]*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PERSONAL_STATE_FOLLOW_UP = Pattern.compile(
            "(?=.*(?:我|今天|最近|昨晚|现在))(?=.*(?:忙|累|还好|一般|心情|状态|开心|难过|烦|闷|没睡好|脑子.*乱)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CHAT_CONTINUATION = Pattern.compile(
            "(?:(?=.*(?:听我|陪我|和我))(?=.*(?:说|聊))|(?=.*(?:不用|不要).{0,8}(?:方案|建议))(?=.*(?:听|聊))).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern LOW_PRESSURE_CHAT_FOLLOW_UP = Pattern.compile(
            "(?=.*(?:只是|就是|先|暂时).{0,8}(?:了解|问问|看看))(?=.*(?:不着急|慢慢来|先不操作|暂时不操作)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern ACKNOWLEDGEMENT = Pattern.compile(
            "^(?:好|好的|好啦|明白了?|了解了?|知道了?)(?:[，,].*)?(?:先这样|就这样|就用这句|现在先不操作|我只是提个建议|晚点再聊)?[。！!]*$|"
                    + "^(?:好|好的)[，,]?(?:就用|先用|采用)(?:这句|这一版|这个版本)[。！!]*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern ASSISTANT_IDENTITY_FOLLOW_UP = Pattern.compile(
            "^(?:那|那么)?你(?:叫(?:什么|啥)(?:名字)?|的名字(?:是)?什么|是谁|是哪一位|是哪位助手)[？?]?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CAPABILITY_FOLLOW_UP = Pattern.compile(
            "(?=.*(?:你|协助|帮助|帮))(?=.*(?:能|可以|会))"
                    + "(?=.*(?:做哪些事情|做什么|哪些能力|如何协助|怎么协助|如何帮助|怎么帮助|"
                    + "整理哪些|哪些类型的文件|处理哪些文件)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern EXECUTION_POLICY_FOLLOW_UP = Pattern.compile(
            "(?=.*(?:操作|任务|这些))(?=.*(?:不经过确认|无需确认|直接发生|直接执行|会执行|怎么确认)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PRODUCT_REASON_FOLLOW_UP = Pattern.compile(
            "(?=.*(?:为什么|为何|理由|价值|值得|好处))(?=.*(?:这些|这个|功能|它|使用|选择|筛选项|设计|流程)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PRODUCT_FEEDBACK_FOLLOW_UP = Pattern.compile(
            "(?:(?=.*(?:尤其|特别是|主要是|资料多|文件多|等得|比较久))|"
                    + "(?=.*(?:补充|说的是))(?=.*(?:移动端|网页端|搜索页|结果页))|"
                    + "(?=.*(?:只是|仅仅是).{0,8}(?:建议|反馈))(?=.*(?:不要|不用|无需).{0,8}(?:直接)?(?:改|修改|执行))).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern WRITING_REFINEMENT_FOLLOW_UP = Pattern.compile(
            "(?=.*(?:语气|措辞|表达|内容|提醒|文案|这一句|这段))(?=.*(?:再|更|改得|调整|强调|补充|委婉|简洁|正式)).*|"
                    + "^(?:再)?(?:强调|补充|改得|写得|调整).+",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern EXTERNAL_RESOURCE_FOLLOW_UP = Pattern.compile(
            "(?:(?=.*(?:推荐|提供|告诉我|有哪些|哪里))(?=.*(?:官方|公开|合法|正规).{0,8}(?:渠道|网站|平台|来源))|"
                    + "(?=.*(?:那个网站|该网站|外部网站))(?=.*(?:下载|保存).{0,8}(?:云盘|这里))).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern ERRAND_GUIDANCE_FOLLOW_UP = Pattern.compile(
            "(?=.*(?:告诉我|说明|介绍|需要准备|通常要准备|怎么做|如何操作))"
                    + "(?=.*(?:订餐|预约|下单|打车|订票|订酒店|准备哪些信息|哪些信息)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CONSULTATION_ONLY_ACKNOWLEDGEMENT = Pattern.compile(
            "(?=.*(?:先|只是|只|仅仅)?(?:了解|咨询|问问))(?=.*(?:不需要|不用|不要|不打算).{0,8}(?:执行|操作|改动|修改)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern HIGH_STAKES_PERSISTENCE = Pattern.compile(
            "(?=.*(?:不保证准确|不一定准确|大概|大致|随便))(?=.*(?:结论|诊断|判断|推荐|预测)).*|"
                    + "(?=.*(?:只给|给个|给一个))(?=.*(?:结论|诊断|预测|推荐)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    Optional<Decision> evaluate(String message, AssistantConversationState conversation) {
        String value = message == null ? "" : message.trim();
        if (value.isBlank() || conversation == null || conversation.pendingIntentId().isBlank()) {
            return Optional.empty();
        }
        if ("assistant_product_feedback".equals(conversation.pendingIntentId())
                && matches(PRODUCT_FEEDBACK_FOLLOW_UP, value)) {
            return decision(
                    "product_feedback_follow_up",
                    value.contains("只是建议") ? "assistant_acknowledgement" : "assistant_product_feedback",
                    "明白，这是对现有体验的补充反馈，我只会记录和回应建议，不会直接修改流程。"
            );
        }
        if (EXECUTION_CONFIRMATION.matcher(value).matches()) {
            return Optional.empty();
        }
        if (FILE_OPERATION_REQUEST.matcher(value).find()) {
            return Optional.empty();
        }
        if (isResponseOnlyIntent(conversation.pendingIntentId()) && matches(ACKNOWLEDGEMENT, value)) {
            return decision(
                    "response_only_acknowledgement",
                    "assistant_acknowledgement",
                    "明白，当前对话到这里即可，我不会创建或执行文件任务。"
            );
        }
        if (isResponseOnlyIntent(conversation.pendingIntentId())
                && (matches(PERSONAL_STATE_FOLLOW_UP, value)
                || matches(LOW_PRESSURE_CHAT_FOLLOW_UP, value))) {
            return decision(
                    "response_only_to_personal_chat",
                    "assistant_chat",
                    "可以，我们先不做文件操作。你可以按自己的节奏继续聊。"
            );
        }

        return switch (conversation.pendingIntentId()) {
            case "assistant_user_identity" -> matches(ASSISTANT_IDENTITY_FOLLOW_UP, value)
                    ? decision("user_identity_to_assistant_identity", "assistant_identity",
                    "我叫安安，是 Alicia 云盘里的文件助手。")
                    : Optional.empty();
            case "assistant_identity" -> matches(CAPABILITY_FOLLOW_UP, value)
                    ? decision("identity_to_capability", "assistant_capability_examples",
                    "我可以帮你查找、整理和规划文件操作；涉及变更时会先生成方案供你确认。")
                    : Optional.empty();
            case "assistant_social" -> matches(PERSONAL_STATE_FOLLOW_UP, value)
                    ? decision("social_to_personal_chat", "assistant_chat",
                    "听起来你今天虽然有些忙，但整体还算平稳。我们可以按你舒服的节奏聊聊。")
                    : Optional.empty();
            case "assistant_chat" -> matches(CHAT_CONTINUATION, value) || matches(PERSONAL_STATE_FOLLOW_UP, value)
                    ? decision("chat_continuation", "assistant_chat",
                    "我在听。你可以按自己的节奏继续说，我们先不处理任何文件。")
                    : Optional.empty();
            case "assistant_product_info" -> matches(PRODUCT_REASON_FOLLOW_UP, value)
                    ? decision("product_reason_follow_up", "assistant_product_reason",
                    "这些能力的价值在于把查找、整理和确认流程集中起来，同时避免未经确认的文件变更。")
                    : Optional.empty();
            case "assistant_product_feedback" -> matches(PRODUCT_REASON_FOLLOW_UP, value)
                    ? decision("product_feedback_to_reason", "assistant_product_reason",
                    "保留这些筛选项是为了让不同文件规模和查找方式都有明确入口，但界面仍可以继续简化。")
                    : Optional.empty();
            case "assistant_writing_help" -> matches(WRITING_REFINEMENT_FOLLOW_UP, value)
                    ? decision("writing_refinement_follow_up", "assistant_writing_help",
                    "可以，我会沿用上一轮文案继续调整措辞，不会执行文案中提到的文件操作。")
                    : Optional.empty();
            case "assistant_external_resource" -> matches(EXTERNAL_RESOURCE_FOLLOW_UP, value)
                    ? decision("external_resource_follow_up", "assistant_external_resource",
                    "我不能代为查找或提供外部资源链接，但可以建议你使用公开、正规且有授权的官方渠道。")
                    : Optional.empty();
            case "assistant_errand_unsupported" -> matches(ERRAND_GUIDANCE_FOLLOW_UP, value)
                    ? decision("errand_guidance_follow_up", "assistant_errand_unsupported",
                    "我不能代替你完成外部预约或下单，但可以说明通常需要准备的信息。")
                    : Optional.empty();
            case "assistant_capability_examples" -> matches(CONSULTATION_ONLY_ACKNOWLEDGEMENT, value)
                    ? decision("capability_consultation_acknowledgement", "assistant_acknowledgement",
                    "明白，这次只了解能力，不会创建或执行任何文件任务。")
                    : matches(EXECUTION_POLICY_FOLLOW_UP, value)
                    ? decision("capability_to_execution_policy", "assistant_execution_explanation",
                    "文件变更不会在未确认时直接发生；系统会先形成待确认任务，再由云端执行并记录结果。")
                    : Optional.empty();
            case "fallback" -> matches(HIGH_STAKES_PERSISTENCE, value)
                    ? decision("high_stakes_persistence", "fallback",
                    "即使降低准确性要求，我也不会给出可能误导你的金融预测或医疗诊断结论。")
                    : Optional.empty();
            default -> Optional.empty();
        };
    }

    private boolean matches(Pattern pattern, String value) {
        return pattern.matcher(value).matches();
    }

    private Optional<Decision> decision(String id, String intentId, String assistantText) {
        return Optional.of(new Decision(id, intentId, "FOLLOW_UP", "RESPOND", assistantText));
    }

    private boolean isResponseOnlyIntent(String intentId) {
        return intentId != null && (intentId.startsWith("assistant_") || "fallback".equals(intentId));
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
