package com.alicia.cloudstorage.rag.assistant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PendingSlotResolutionService {

    private static final int MAX_SLOT_VALUE_LENGTH = 255;
    private static final Pattern QUOTED_RENAME_VALUE = Pattern.compile(
            "^(?:(?:那就|就|那)\\s*)?(?:(?:新(?:文件)?名(?:字|称)?|名称|名字)(?:就)?(?:是|为|用|叫做?|改成|改为)"
                    + "|改成|改为|重命名为|命名为|叫做?)?\\s*(" + TextSupport.QUOTED_NODE_NAME + ")\\.?$"
    );
    private static final Pattern RENAME_VALUE = Pattern.compile(
            "^(?:改成|改为|重命名为|名称改为|名字改为)\\s*[“\"']?(.+?)[”\"']?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EXPLICIT_FOLDER_NAME = Pattern.compile(
            "^(?:那就|就|那)?\\s*(?:(?:(?:取名|命名)(?:为|是)?|(?:名字|名称|目录名|文件夹名)(?:是|为|用|叫做?)|叫做?)\\s*)?("
                    + TextSupport.QUOTED_NODE_NAME + ")\\.?$"
                    + "|^(?:那就|就|那)?(?:取名|命名)(?:为|是)?\\s*([^，,。；;!?！？]+)$"
    );
    private static final Pattern DESTINATION_VALUE = Pattern.compile(
            "^(?:到|至|进|移动到|移到|上传到|上传至|放到|放进)\\s*[“\"']?(.+?)[”\"']?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OPERATION_COMMAND = Pattern.compile(
            "(?:删除|移除|移动|移到|重命名|改名|分享|上传|导入|新建|创建|打开|进入|搜索|查找|列出|展示|下载|恢复)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COLLECTION_REFERENCE = Pattern.compile(
            "(?:所有|全部|全量|批量|剩下|其余|另一个|第[一二三四五六七八九十0-9]+个|根目录下|当前目录下|文件夹中|目录中)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> GENERIC_NODE_NAMES = Set.of(
            "文件", "文件夹", "目录", "图片", "照片", "视频", "影片", "音频", "文档", "压缩包", "它", "这个", "那个"
    );
    private static final Set<String> TEXT_SLOTS = Set.of("target_name", "new_name", "new_folder_name", "target_folder");

    Resolution resolve(
            String message,
            AssistantConversationState conversation,
            IntentRecognitionResponse response
    ) {
        if (conversation == null
                || response == null
                || !conversation.hasPendingSlots()
                || AssistantFlowPolicy.isCapabilityBoundary(response)) {
            return Resolution.unresolved();
        }
        String folderName = explicitFolderName(message, conversation);
        if (!folderName.isBlank()) {
            Map<String, Object> values = Map.of("new_folder_name", folderName);
            return new Resolution(true, values, slotFillFrame(conversation, response, values));
        }
        if (isIndependentOperation(response, conversation)) {
            return Resolution.unresolved();
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (String slot : conversation.pendingSlots()) {
            if (!TEXT_SLOTS.contains(slot)) {
                continue;
            }
            if ("new_name".equals(slot)) {
                String quoted = quotedRenameName(message);
                if (!quoted.isBlank()) {
                    values.put(slot, quoted);
                    continue;
                }
            }
            String structuredValue = "new_folder_name".equals(slot)
                    ? ""
                    : structuredValue(slot, response);
            if (!structuredValue.isBlank()
                    && isExplicitSlotFragment(slot, message, response, conversation)
                    && !("target_name".equals(slot) && GENERIC_NODE_NAMES.contains(structuredValue))) {
                values.put(slot, structuredValue);
            }
        }

        if (values.isEmpty() && conversation.pendingSlots().size() == 1) {
            String slot = conversation.pendingSlots().getFirst();
            String textualValue = textualValue(slot, message, response, conversation);
            if (!textualValue.isBlank()) {
                values.put(slot, textualValue);
            }
        }
        if (values.isEmpty()) {
            return Resolution.unresolved();
        }
        if (values.containsKey("target_folder")
                && "node_move".equals(conversation.pendingIntentId())
                && Boolean.TRUE.equals(response.entities().get(OperationArgumentResolver.LITERAL_MOVE_DESTINATION))
                && values.get("target_folder").equals(response.entities().get("target_folder"))) {
            values.put(OperationArgumentResolver.LITERAL_MOVE_DESTINATION, true);
        }
        SemanticFrame frame = slotFillFrame(conversation, response, values);
        return new Resolution(true, Map.copyOf(values), frame);
    }

    static String explicitFolderName(String message, AssistantConversationState conversation) {
        if (!conversation.pendingSlots().contains("new_folder_name")) {
            return "";
        }
        Matcher name = EXPLICIT_FOLDER_NAME.matcher(clean(message));
        if (!name.matches()) {
            return "";
        }
        String surface = name.group(1);
        return surface == null ? cleanSlotValue(name.group(2))
                : validatedSlotValue(TextSupport.unwrapNodeName(surface));
    }

    static String quotedRenameName(String message) {
        Matcher name = QUOTED_RENAME_VALUE.matcher(clean(message));
        return name.matches() ? validatedSlotValue(TextSupport.unwrapNodeName(name.group(1))) : "";
    }

    private boolean isIndependentOperation(
            IntentRecognitionResponse response,
            AssistantConversationState conversation
    ) {
        if ("fallback".equals(response.intentId())) {
            return false;
        }
        return !response.intentId().equals(conversation.pendingIntentId());
    }

    private String textualValue(
            String slot,
            String message,
            IntentRecognitionResponse response,
            AssistantConversationState conversation
    ) {
        String value = clean(message);
        if (value.isBlank() || value.length() > MAX_SLOT_VALUE_LENGTH || containsControlCharacter(value)) {
            return "";
        }
        if (!isExplicitSlotFragment(slot, value, response, conversation)) {
            return "";
        }
        return switch (slot) {
            case "target_name" -> safeStandaloneNodeName(value, response, conversation) ? unquote(value) : "";
            case "new_name" -> renameValue(value, response, conversation);
            case "new_folder_name" -> safeStandaloneNodeName(value, response, conversation) ? cleanSlotValue(value) : "";
            case "target_folder" -> destinationValue(value, response, conversation);
            default -> "";
        };
    }

    private boolean isExplicitSlotFragment(
            String slot,
            String message,
            IntentRecognitionResponse response,
            AssistantConversationState conversation
    ) {
        String value = clean(message);
        return switch (slot) {
            case "target_name" -> isStandaloneSlotValue(value, response, conversation);
            case "new_name" -> RENAME_VALUE.matcher(value).matches()
                    || isStandaloneSlotValue(value, response, conversation);
            case "new_folder_name" -> isStandaloneSlotValue(value, response, conversation);
            case "target_folder" -> DESTINATION_VALUE.matcher(value).matches()
                    || isStandaloneSlotValue(value, response, conversation);
            default -> false;
        };
    }

    private boolean isStandaloneSlotValue(
            String value,
            IntentRecognitionResponse response,
            AssistantConversationState conversation
    ) {
        SemanticFrame frame = response.semanticFrame();
        boolean semanticSlotFill = frame != null
                && List.of("SLOT_FILL", "CORRECTION").contains(frame.relation());
        boolean samePendingIntent = conversation != null
                && conversation.hasPendingSlots()
                && response.intentId().equals(conversation.pendingIntentId());
        String structure = TextSupport.maskQuotedNodeNames(value);
        return (isGenericUnknownClarification(response) || semanticSlotFill || samePendingIntent)
                && !OPERATION_COMMAND.matcher(structure).find()
                && !COLLECTION_REFERENCE.matcher(structure).find();
    }

    private boolean safeStandaloneNodeName(
            String value,
            IntentRecognitionResponse response,
            AssistantConversationState conversation
    ) {
        if (!isStandaloneSlotValue(value, response, conversation)) {
            return false;
        }
        String unquoted = unquote(value);
        return !unquoted.isBlank() && !GENERIC_NODE_NAMES.contains(unquoted.toLowerCase(Locale.ROOT));
    }

    private String renameValue(
            String value,
            IntentRecognitionResponse response,
            AssistantConversationState conversation
    ) {
        String structured = stringValue(response.entities().get("new_name"));
        if (!structured.isBlank()) {
            return cleanSlotValue(structured);
        }
        Matcher matcher = RENAME_VALUE.matcher(value);
        if (matcher.matches()) {
            return cleanSlotValue(matcher.group(1));
        }
        return safeStandaloneNodeName(value, response, conversation) ? cleanSlotValue(value) : "";
    }

    private String destinationValue(
            String value,
            IntentRecognitionResponse response,
            AssistantConversationState conversation
    ) {
        String structured = structuredValue("target_folder", response);
        if (!structured.isBlank()) {
            return structured;
        }
        Matcher matcher = DESTINATION_VALUE.matcher(value);
        if (matcher.matches()) {
            return cleanSlotValue(matcher.group(1));
        }
        return safeStandaloneNodeName(value, response, conversation) ? cleanSlotValue(value) : "";
    }

    private boolean isGenericUnknownClarification(IntentRecognitionResponse response) {
        SemanticFrame frame = response.semanticFrame();
        if (frame == null) {
            return "fallback".equals(response.intentId());
        }
        String reason = frame.clarification() == null ? "" : frame.clarification().reason();
        return "fallback".equals(response.intentId())
                && "UNKNOWN".equals(frame.operation())
                && (reason.isBlank() || "operation".equalsIgnoreCase(reason));
    }

    private SemanticFrame slotFillFrame(
            AssistantConversationState conversation,
            IntentRecognitionResponse response,
            Map<String, Object> values
    ) {
        SemanticFrame stored = conversation.semanticFrame();
        SemanticFrame.Query query = stored.query();
        if (values.containsKey("target_name")) {
            String targetName = stringValue(values.get("target_name"));
            String resultType = stringValue(response.entities().get("result_type"));
            if (resultType.isBlank()) {
                resultType = query.resultType();
            }
            query = new SemanticFrame.Query(
                    "NAME_EXACT",
                    resultType.isBlank() ? "ANY" : resultType,
                    targetName,
                    normalizeName(targetName),
                    query.filters()
            );
        }
        return new SemanticFrame(
                SemanticFrame.VERSION,
                "SLOT_FILL",
                stored.operation(),
                query,
                stored.scope(),
                stored.reference(),
                Math.max(stored.confidence(), response.confidence()),
                List.of(),
                SemanticFrame.Clarification.empty()
        );
    }

    private String structuredValue(String slot, IntentRecognitionResponse response) {
        String value = stringValue(response.entities().get(slot));
        if ("target_folder".equals(slot)
                && Boolean.TRUE.equals(response.entities().get(OperationArgumentResolver.LITERAL_MOVE_DESTINATION))) {
            return validatedSlotValue(value);
        }
        return cleanSlotValue(value);
    }

    private static String cleanSlotValue(String value) {
        return validatedSlotValue(unquote(clean(value)));
    }

    private static String validatedSlotValue(String value) {
        return value.length() <= MAX_SLOT_VALUE_LENGTH && !containsControlCharacter(value) ? value : "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("[。！？!?]+$", "").trim();
    }

    private static String unquote(String value) {
        return TextSupport.unwrapNodeName(clean(value)).replaceAll("^[“\"']|[”\"']$", "").trim();
    }

    private static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalizeName(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    record Resolution(boolean resolved, Map<String, Object> values, SemanticFrame semanticFrame) {
        static Resolution unresolved() {
            return new Resolution(false, Map.of(), SemanticFrame.empty());
        }
    }
}
