package com.alicia.cloudstorage.rag.assistant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves explicit corrections and resumptions for a previously established operation.
 * It never treats an unqualified pronoun or generic confirmation as an operation continuation.
 */
final class ContextualOperationFollowUpGuard {

    private static final Pattern SEARCH_CORRECTION = Pattern.compile(
            "(?:说错了|不是.+(?:而是|是).+|范围只|只看当前目录|只查当前目录)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern SEARCH_REPLACEMENT = Pattern.compile(
            "(?:不是[^，,。；;]+[，,]?\\s*(?:而是|是)|改成|换成)\\s*([^，,。；;!?！？]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern RENAME_NEW_NAME = Pattern.compile(
            "(?:新名字|新名称|名称|名字)(?:应该)?(?:改成|改为|换成|是)\\s*[“\\\"']?([^”\\\"'，,。；;!?！？]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern SOURCE_NAME = Pattern.compile(
            "(?:源文件|源目录|对象)(?:还是|仍是|是)\\s*[“\\\"']?([^”\\\"'，,。；;!?！？]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CREATE_NAME = Pattern.compile(
            "(?:名称|名字)(?:改成|改为|换成)\\s*[“\\\"']?([^”\\\"'，,。；;!?！？]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MOVE_DESTINATION = Pattern.compile(
            "(?:目的地|目标(?:目录|文件夹)?)(?:改成|改为|换成|是)\\s*[“\\\"']?([^”\\\"'，,。；;!?！？]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern EXPLICIT_RESUME = Pattern.compile(
            "(?:(?:回到|继续|接着).{0,10}(?:刚才|之前).{0,10}(?:重命名|改名|移动|创建|新建|搜索|查找|上传|删除)|"
                    + "回到(?:刚才|之前)?(?:的)?(?:搜索|查找|重命名|改名|移动|创建|新建|上传|删除))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern KEEP_CREATE_CANCEL_UPLOAD = Pattern.compile(
            "(?=.*(?:取消|不要|别|不再).{0,8}上传)(?=.*(?:只|仅).{0,4}(?:创建|新建|建)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern CREATE_EXPLICIT_NAME = Pattern.compile(
            "(?:只|仅)(?:创建|新建|建)\\s*(?:一个)?\\s*([^，,。；;!?！？]+?)(?:目录|文件夹)(?:[。！!]*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern KEEP_RENAME_CANCEL_DELETE = Pattern.compile(
            "(?=.*(?:只|仅).{0,4}(?:重命名|改名|改成|改为))(?=.*(?:别|不要|取消|不再).{0,8}(?:删除|删)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MOVE_SINGLE_SOURCE = Pattern.compile(
            "^(?:(?:那|改成|换成)\\s*)?(?:(?:不用|不要)(?:全部|都)[，,]\\s*)?(?:那\\s*)?"
                    + "(?:先)?(?:只|仅)(?:把)?(?:移动|搬|挪)(?:一下)?\\s*"
                    + "([^，,。；;!?！？]+?)(?:文件)?(?:[。！!]*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MOVE_SINGLE_SOURCE_WITH_DESTINATION = Pattern.compile(
            "^(?:(?:那|改成|换成)\\s*)?(?:(?:不用|不要)(?:全部|都)[，,]\\s*)?(?:那\\s*)?"
                    + "(?:先)?(?:只|仅)(?:把)?(?:移动|搬|挪)(?:一下)?\\s*"
                    + "(.+?)(?:到|至|进)\\s*([^，,。；;!?！？]+?)(?:[。！!]*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MOVE_INDEX_WITH_EXPLICIT_NAME = Pattern.compile(
            "^(?:那\\s*)?(?:只|仅)(?:把)?(?:移动|搬|挪)"
                    + "第[一二两三四五六七八九十0-9]+个[，,]\\s*"
                    + "(?:文件名|名称|名字)(?:是|为)\\s*([^，,。；;!?！？]+)[，,]\\s*"
                    + "(?:目标|目的地)(?:是|为)\\s*([^，,。；;!?！？]+?)(?:[。！!]*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern DELETE_SINGLE_SOURCE = Pattern.compile(
            "^(?:(?:那|改成|换成)\\s*)?(?:只|仅)(?:把)?(?:删除|删掉|删)\\s*"
                    + "([^，,。；;!?！？]+?)(?:文件)?(?:[。！!]*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern SHARE_SINGLE_SOURCE = Pattern.compile(
            "^(?:(?:那|改成|换成)\\s*)?(?:只|仅)(?:为|给)?\\s*"
                    + "([^，,。；;!?！？]+?)(?:生成|创建)?(?:一个)?(?:分享|共享)?链接(?:[。！!]*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern COLLECTION_CHOICE_VERB_FIRST = Pattern.compile(
            "^(?:先)?只(?:把)?(?:移动|搬|归档)\\s*(图片|照片|相片|截图|视频|影片|音频|音乐|文档|表格|压缩包|压缩文件)"
                    + "(?:文件)?(?:到|至|进)\\s*([^，,。；;!?！？]+?)(?:目录|文件夹)?(?:[。！!]*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern COLLECTION_CHOICE_OBJECT_FIRST = Pattern.compile(
            "^(?:先)?只(?:把)?(图片|照片|相片|截图|视频|影片|音频|音乐|文档|表格|压缩包|压缩文件)"
                    + "(?:文件)?(?:都)?(?:移动|搬|归档)(?:到|至|进)\\s*([^，,。；;!?！？]+?)(?:目录|文件夹)?(?:[。！!]*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    Optional<Decision> evaluate(String message, AssistantConversationState conversation) {
        String value = clean(message);
        if (value.isBlank() || conversation == null) {
            return Optional.empty();
        }

        Optional<Decision> narrowed = explicitNarrowing(value, conversation);
        if (narrowed.isPresent()) {
            return narrowed;
        }

        Optional<OperationContext> immediate = immediateContext(conversation);
        if (immediate.isPresent()) {
            Optional<Decision> correction = correction(value, immediate.get(), false);
            if (correction.isPresent()) {
                return correction;
            }
        }

        if (!EXPLICIT_RESUME.matcher(value).find()) {
            return Optional.empty();
        }
        return suspendedContext(conversation).flatMap(context -> correction(value, context, true));
    }

    private Optional<Decision> explicitNarrowing(
            String message,
            AssistantConversationState conversation
    ) {
        Map<String, Object> entities = new LinkedHashMap<>(conversation.entities());
        String pendingIntent = conversation.pendingIntentId() == null ? "" : conversation.pendingIntentId();

        if (isCompositeContext(conversation) && KEEP_CREATE_CANCEL_UPLOAD.matcher(message).matches()) {
            Matcher name = CREATE_EXPLICIT_NAME.matcher(message);
            if (name.find()) {
                entities.put("new_folder_name", cleanValue(name.group(1)));
            }
            entities.keySet().removeIf(key -> !List.of("new_folder_name", "target_folder", "scope").contains(key));
            return decision(
                    "keep_create_cancel_upload",
                    "folder_create",
                    "CORRECTION",
                    "CREATE_FOLDER",
                    entities
            );
        }

        if ("fallback".equals(pendingIntent) && KEEP_RENAME_CANCEL_DELETE.matcher(message).matches()) {
            entities.keySet().removeIf(key -> !List.of("target_name", "new_name").contains(key));
            return decision(
                    "keep_rename_cancel_delete",
                    "file_rename",
                    "CORRECTION",
                    "RENAME",
                    entities
            );
        }

        if (isMoveContext(pendingIntent)) {
            Matcher indexed = MOVE_INDEX_WITH_EXPLICIT_NAME.matcher(message);
            if (indexed.matches()) {
                return exactMoveDecision(entities, indexed.group(1), indexed.group(2));
            }
            Matcher withDestination = MOVE_SINGLE_SOURCE_WITH_DESTINATION.matcher(message);
            if (withDestination.matches()) {
                return exactMoveDecision(entities, withDestination.group(1), withDestination.group(2));
            }
            Matcher source = MOVE_SINGLE_SOURCE.matcher(message);
            if (source.matches() && entities.containsKey("target_folder")) {
                return exactMoveDecision(entities, source.group(1), null);
            }
        }

        if ("file_delete".equals(pendingIntent)) {
            Matcher source = DELETE_SINGLE_SOURCE.matcher(message);
            String targetName = source.matches() ? cleanValue(source.group(1)) : "";
            if (isExplicitTargetName(targetName)) {
                entities.put("target_name", targetName);
                entities.put("result_type", resultType(targetName));
                entities.keySet().removeIf(key -> !List.of("target_name", "result_type").contains(key));
                return decision(
                        "narrow_delete_to_single_source",
                        "file_delete",
                        "CORRECTION",
                        "DELETE",
                        entities
                );
            }
        }

        if ("file_share".equals(pendingIntent)) {
            Matcher source = SHARE_SINGLE_SOURCE.matcher(message);
            String targetName = source.matches() ? cleanValue(source.group(1)) : "";
            if (isExplicitTargetName(targetName)) {
                entities.put("target_name", targetName);
                entities.put("result_type", resultType(targetName));
                entities.keySet().removeIf(key -> !List.of("target_name", "result_type").contains(key));
                return decision(
                        "narrow_share_to_single_source",
                        "file_share",
                        "CORRECTION",
                        "SHARE",
                        entities
                );
            }
        }

        if ("fallback".equals(pendingIntent)) {
            Optional<Decision> collectionChoice = collectionChoice(message);
            if (collectionChoice.isPresent()) {
                return collectionChoice;
            }
        }
        return Optional.empty();
    }

    private Optional<Decision> exactMoveDecision(
            Map<String, Object> entities,
            String source,
            String destination
    ) {
        String targetName = cleanValue(source);
        if (!isExplicitTargetName(targetName)) {
            return Optional.empty();
        }
        entities.put("target_name", targetName);
        entities.put("result_type", resultType(targetName));
        if (destination != null && !destination.isBlank()) {
            entities.put("target_folder", cleanValue(destination));
        }
        entities.keySet().removeIf(key -> !List.of("target_name", "target_folder", "result_type").contains(key));
        return decision(
                "narrow_move_to_single_source",
                "node_move",
                "CORRECTION",
                "MOVE",
                entities
        );
    }

    private boolean isMoveContext(String intentId) {
        return List.of(
                "node_move",
                "collection_move_by_category",
                "collection_move_by_extension",
                "collection_move_by_name"
        ).contains(intentId);
    }

    private boolean isExplicitTargetName(String value) {
        return value != null
                && !value.isBlank()
                && !value.matches("(?:它|这个|那个|这些|那些|文件|文档|资料|文件夹|目录)")
                && !value.matches("(?:第[一二两三四五六七八九十0-9]+个|最近|最早|最后|前几个|后几个).*");
    }

    private String resultType(String value) {
        if (value.endsWith("目录") || value.endsWith("文件夹")) {
            return "FOLDER";
        }
        return value.matches("(?i).*\\.[a-z0-9]{1,16}$") ? "FILE" : "ANY";
    }

    private Optional<Decision> collectionChoice(String message) {
        Matcher verbFirst = COLLECTION_CHOICE_VERB_FIRST.matcher(message);
        if (verbFirst.matches()) {
            return collectionChoice(verbFirst.group(1), verbFirst.group(2));
        }
        Matcher objectFirst = COLLECTION_CHOICE_OBJECT_FIRST.matcher(message);
        if (objectFirst.matches()) {
            return collectionChoice(objectFirst.group(1), objectFirst.group(2));
        }
        return Optional.empty();
    }

    private Optional<Decision> collectionChoice(String fileType, String targetFolder) {
        return decision(
                "narrow_collection_move_to_single_category",
                "collection_move_by_category",
                "CORRECTION",
                "MOVE",
                Map.of(
                        "file_type", cleanValue(fileType),
                        "target_folder", cleanValue(targetFolder)
                )
        );
    }

    private boolean isCompositeContext(AssistantConversationState conversation) {
        if ("folder_create_then_upload".equals(conversation.pendingIntentId())) {
            return true;
        }
        AssistantConversationFocus focus = conversation.focus();
        return focus != null && "folder_create_then_upload".equals(focus.sourceIntentId());
    }

    private Optional<Decision> correction(String message, OperationContext context, boolean resumed) {
        Map<String, Object> entities = new LinkedHashMap<>(context.entities());
        return switch (context.intentId()) {
            case "file_search" -> searchCorrection(message, entities, resumed);
            case "file_rename" -> renameCorrection(message, entities, resumed);
            case "folder_create" -> createCorrection(message, entities, resumed);
            case "node_move" -> moveCorrection(message, entities, resumed);
            default -> Optional.empty();
        };
    }

    private Optional<Decision> searchCorrection(
            String message,
            Map<String, Object> entities,
            boolean resumed
    ) {
        if (!resumed && !SEARCH_CORRECTION.matcher(message).find()) {
            return Optional.empty();
        }
        Matcher replacement = SEARCH_REPLACEMENT.matcher(message);
        if (replacement.find()) {
            entities.put("target_name", cleanValue(replacement.group(1)));
            entities.put("query_mode", "name_search");
        }
        if (message.contains("当前目录")) {
            entities.put("scope", "current");
        } else if (message.contains("全部云盘") || message.contains("整个云盘") || message.contains("全盘")) {
            entities.put("scope", "all");
        }
        return decision("search_correction", "file_search", "CORRECTION", "SEARCH", entities);
    }

    private Optional<Decision> renameCorrection(
            String message,
            Map<String, Object> entities,
            boolean resumed
    ) {
        boolean changed = false;
        boolean missingSource = !contextHasValue(entities, "target_name");
        Matcher newName = RENAME_NEW_NAME.matcher(message);
        if (newName.find()) {
            entities.put("new_name", cleanValue(newName.group(1)));
            changed = true;
        }
        Matcher source = SOURCE_NAME.matcher(message);
        boolean sourceMatched = source.find();
        if (sourceMatched) {
            entities.put("target_name", cleanValue(source.group(1)));
            changed = true;
        }
        if (!changed) {
            return Optional.empty();
        }
        String relation = resumed && missingSource && sourceMatched ? "SLOT_FILL" : "CORRECTION";
        return decision(
                resumed ? "resume_rename" : "rename_correction",
                "file_rename",
                relation,
                "RENAME",
                entities
        );
    }

    private Optional<Decision> createCorrection(
            String message,
            Map<String, Object> entities,
            boolean resumed
    ) {
        Matcher name = CREATE_NAME.matcher(message);
        if (resumed || !name.find()) {
            return Optional.empty();
        }
        entities.put("new_folder_name", cleanValue(name.group(1)));
        return decision("create_name_correction", "folder_create", "CORRECTION", "CREATE_FOLDER", entities);
    }

    private Optional<Decision> moveCorrection(
            String message,
            Map<String, Object> entities,
            boolean resumed
    ) {
        Matcher destination = MOVE_DESTINATION.matcher(message);
        if (resumed || !destination.find()) {
            return Optional.empty();
        }
        entities.put("target_folder", cleanValue(destination.group(1)));
        return decision("move_destination_correction", "node_move", "CORRECTION", "MOVE", entities);
    }

    private Optional<OperationContext> immediateContext(AssistantConversationState conversation) {
        String intentId = conversation.pendingIntentId() == null ? "" : conversation.pendingIntentId();
        if (!supportedIntent(intentId)) {
            return Optional.empty();
        }
        return Optional.of(new OperationContext(intentId, conversation.entities()));
    }

    private Optional<OperationContext> suspendedContext(AssistantConversationState conversation) {
        AssistantConversationFocus focus = conversation.focus();
        if (focus == null || !supportedIntent(focus.sourceIntentId())) {
            return Optional.empty();
        }
        if (!focus.hasResumableOperationContext()) {
            return Optional.empty();
        }
        return Optional.of(new OperationContext(focus.sourceIntentId(), focus.entities()));
    }

    private boolean supportedIntent(String intentId) {
        return List.of("file_search", "file_rename", "folder_create", "node_move").contains(intentId);
    }

    private Optional<Decision> decision(
            String id,
            String intentId,
            String relation,
            String operation,
            Map<String, Object> entities
    ) {
        return Optional.of(new Decision(id, intentId, relation, operation, Map.copyOf(entities)));
    }

    private boolean contextHasValue(Map<String, Object> entities, String key) {
        Object value = entities.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return false;
        }
        if (!"target_name".equals(key)) {
            return true;
        }
        String normalized = String.valueOf(value).trim();
        return !List.of("我要", "我想", "想要", "一个文件", "某个文件", "文件").contains(normalized);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String cleanValue(String value) {
        return clean(value).replaceAll("^[“\\\"']|[”\\\"']$", "").trim();
    }

    private record OperationContext(String intentId, Map<String, Object> entities) {
        private OperationContext {
            entities = entities == null ? Map.of() : Map.copyOf(entities);
        }
    }

    record Decision(
            String id,
            String intentId,
            String relation,
            String operation,
            Map<String, Object> entities
    ) {
        SemanticFrame applyTo(SemanticFrame frame) {
            return new SemanticFrame(
                    frame.schemaVersion(),
                    relation,
                    operation,
                    frame.query(),
                    frame.scope(),
                    frame.reference(),
                    frame.confidence(),
                    frame.ambiguities(),
                    frame.clarification()
            );
        }
    }
}
