package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConversationContextResolver {

    private static final String SCHEMA_VERSION = "intent_recognition_v1";

    private final ConversationContextModelClient modelClient;
    private final IntentRecognitionService intentRecognitionService;
    private final Settings settings;

    public ConversationContextResolver(
            ConversationContextModelClient modelClient,
            IntentRecognitionService intentRecognitionService,
            RagConfigLoader configLoader
    ) {
        this.modelClient = modelClient;
        this.intentRecognitionService = intentRecognitionService;
        this.settings = Settings.load(
                configLoader.loadJson("rag/conversation/context_resolution.json"),
                configLoader.loadJson("rag/conversation/query_rules.json").path("candidate_selection")
        );
    }

    public ContextAttempt resolve(
            String message,
            AssistantConversationState conversation,
            IntentRecognitionResponse baseResponse
    ) {
        return resolve(message, conversation, baseResponse, null);
    }

    public ContextAttempt resolve(
            String message,
            AssistantConversationState conversation,
            IntentRecognitionResponse baseResponse,
            SemanticFrame semanticFrame
    ) {
        if (!settings.enabled() || conversation == null || conversation.focus() == null) {
            return ContextAttempt.notApplied();
        }

        AssistantConversationFocus focus = conversation.focus();
        ConversationContextResolution localResolution = localResolve(message, focus, baseResponse);
        boolean semanticAuthority = semanticFrame != null
                && SemanticFrame.VERSION.equals(semanticFrame.schemaVersion());
        Optional<ConversationContextResolution> modelResolution = semanticAuthority
                ? Optional.empty()
                : modelClient
                .resolve(message, conversation, baseResponse)
                .map(result -> resolutionFromModel(result.payload()))
                .filter(item -> item.confidence() >= settings.minConfidence());
        ConversationContextResolution resolution = modelResolution
                .filter(item -> shouldTrustModelResolution(item, localResolution))
                .orElse(localResolution);
        resolution = completeResolution(message, focus, resolution);

        if (!resolution.continuesContext() && !resolution.needsClarification()) {
            return ContextAttempt.notApplied();
        }

        if (resolution.needsClarification()) {
            return ContextAttempt.answer(clarificationResponse(message, conversation, resolution));
        }

        if (resolution.shouldAnswerDirectly()) {
            return ContextAttempt.answer(directAnswerResponse(message, conversation, resolution));
        }

        if (resolution.shouldRewrite()) {
            IntentRecognitionResponse rewritten = intentRecognitionService.recognize(resolution.rewrittenMessage());
            if (!resolution.carriedEntities().isEmpty()) {
                Map<String, Object> merged = new LinkedHashMap<>(rewritten.entities());
                merged.putAll(resolution.carriedEntities());
                rewritten = intentRecognitionService.rebuildForConversation(
                        rewritten,
                        rewritten.intentId(),
                        merged,
                        resolution.reason().isBlank() ? "根据上下文改写后合并实体。" : resolution.reason()
                );
            }
            return ContextAttempt.rewritten(rewritten, resolution);
        }

        return ContextAttempt.notApplied();
    }

    private boolean shouldTrustModelResolution(
            ConversationContextResolution modelResolution,
            ConversationContextResolution localResolution
    ) {
        return !"new_task".equals(modelResolution.relation())
                || (!localResolution.continuesContext() && !localResolution.needsClarification());
    }

    private ConversationContextResolution completeResolution(
            String message,
            AssistantConversationFocus focus,
            ConversationContextResolution resolution
    ) {
        if (!"follow_up_question".equals(resolution.relation())) {
            return resolution;
        }
        if ("selected_candidate".equals(resolution.referent()) && !resolution.answerText().isBlank()) {
            return resolution;
        }
        if (!focus.hasCandidateContext()) {
            return new ConversationContextResolution(
                    "clarification_required",
                    "none",
                    resolution.contextAction(),
                    "",
                    Map.of(),
                    List.of(),
                    "",
                    settings.message("missing_context"),
                    Math.max(resolution.confidence(), 0.8),
                    resolution.reason()
            );
        }
        if (!focus.hasSingleCandidateFocus()) {
            return new ConversationContextResolution(
                    "clarification_required",
                    "previous_candidate_set",
                    resolution.contextAction(),
                    "",
                    Map.of(),
                    List.of(),
                    "",
                    settings.message("multiple_candidates"),
                    Math.max(resolution.confidence(), 0.86),
                    resolution.reason()
            );
        }
        QuestionField field = settings.questionField(resolution.contextAction())
                .or(() -> questionField(normalize(message)))
                .orElse(settings.defaultQuestionField());
        return new ConversationContextResolution(
                resolution.relation(),
                resolution.referent(),
                field.id(),
                resolution.rewrittenMessage(),
                resolution.carriedEntities(),
                    resolution.requiredClientFields(),
                answerFor(candidateForResolution(focus, resolution), field),
                resolution.clarificationQuestion(),
                resolution.selectedIndex(),
                resolution.confidence(),
                resolution.reason()
        );
    }

    private ConversationContextResolution localResolve(
            String message,
            AssistantConversationFocus focus,
            IntentRecognitionResponse baseResponse
    ) {
        String cleanMessage = normalize(message);
        if (cleanMessage.isBlank()) {
            return ConversationContextResolution.newTask("空输入不承接上下文。");
        }

        if (referencesClientInput(cleanMessage, baseResponse)) {
            return ConversationContextResolution.newTask("上传动作中的代词指向客户端已选择内容，不承接云盘候选上下文。");
        }

        if (!referencesContext(cleanMessage)) {
            return ConversationContextResolution.newTask("未检测到上下文引用。");
        }

        if (!focus.hasCandidateContext()) {
            return new ConversationContextResolution(
                    "clarification_required",
                    "none",
                    "",
                    "",
                    Map.of(),
                    List.of(),
                    "",
                    settings.message("missing_context"),
                    0.8,
                    "用户使用了承接表达，但没有可用候选上下文。"
            );
        }

        CandidateSelection selectedReference = selectedReference(cleanMessage, focus);
        if (selectedReference.outOfRange()) {
            return new ConversationContextResolution(
                    "clarification_required",
                    "previous_candidate_set",
                    "",
                    "",
                    Map.of(),
                    List.of(),
                    "",
                    selectedReference.message(),
                    0.88,
                    "用户选择的上一轮候选序号超出范围。"
            );
        }

        String mutationAction = mutationAction(cleanMessage);
        if (!mutationAction.isBlank()) {
            if (selectedReference.selectedCandidate() != null) {
                return rewriteMutation(cleanMessage, selectedReference.selectedCandidate(), mutationAction, selectedReference.oneBasedIndex());
            }
            return rewriteMutation(cleanMessage, focus, mutationAction);
        }

        Optional<QuestionField> field = questionField(cleanMessage);
        if (field.isPresent() || asksFollowUpQuestion(cleanMessage)) {
            CandidateItem selectedCandidate = selectedReference.selectedCandidate();
            Integer selectedIndex = selectedReference.oneBasedIndex();
            if (selectedCandidate == null && focus.hasSingleCandidateFocus()) {
                selectedCandidate = focus.effectiveCandidate();
                selectedIndex = null;
            }
            if (selectedCandidate == null) {
                return new ConversationContextResolution(
                        "clarification_required",
                        "previous_candidate_set",
                        "",
                        "",
                        Map.of(),
                        List.of(),
                        "",
                        settings.message("multiple_candidates"),
                        0.86,
                        "上一轮存在多个候选，无法确定代词指代。"
                );
            }

            QuestionField questionField = field.orElse(settings.defaultQuestionField());
            String answer = answerFor(selectedCandidate, questionField);
            return new ConversationContextResolution(
                    "follow_up_question",
                    selectedIndex == null ? "previous_candidate" : "selected_candidate",
                    questionField.id(),
                    "",
                    Map.of(),
                    List.of(),
                    answer,
                    "",
                    selectedIndex,
                    0.84,
                    "根据上一轮候选回答文件上下文追问。"
            );
        }

        return ConversationContextResolution.newTask("承接表达未形成可解析上下文动作。");
    }

    private boolean referencesClientInput(String message, IntentRecognitionResponse baseResponse) {
        if (baseResponse == null || baseResponse.actionDraft() == null) {
            return false;
        }
        String actionType = baseResponse.actionDraft().type();
        return settings.clientInputActionTypes().contains(actionType)
                && (TextSupport.containsAny(message, settings.referenceTerms())
                || TextSupport.containsAny(message, settings.setReferenceTerms()));
    }

    private ConversationContextResolution rewriteMutation(
            String message,
            AssistantConversationFocus focus,
            String action
    ) {
        if (!focus.hasSingleCandidateFocus()) {
            return new ConversationContextResolution(
                    "clarification_required",
                    "previous_candidate_set",
                    action,
                    "",
                    Map.of(),
                    List.of(),
                    "",
                    settings.message("multiple_candidates"),
                    0.86,
                    "上一轮存在多个候选，无法确定要处理哪一个。"
            );
        }

        CandidateItem candidate = focus.effectiveCandidate();
        return rewriteMutation(message, candidate, action, null);
    }

    private ConversationContextResolution rewriteMutation(
            String message,
            CandidateItem candidate,
            String action,
            Integer selectedIndex
    ) {
        String rewritten = switch (action) {
            case "delete" -> "删除 " + candidate.name();
            case "share" -> "分享 " + candidate.name();
            case "rename" -> "把 " + candidate.name() + " " + message;
            case "move" -> "把 " + candidate.name() + " " + message;
            case "upload" -> message;
            default -> "";
        };
        Map<String, Object> carried = new LinkedHashMap<>();
        carried.put("target_name", candidate.name());
        return new ConversationContextResolution(
                "continue_previous_action",
                selectedIndex == null ? "previous_candidate" : "selected_candidate",
                action,
                rewritten,
                carried,
                List.of(),
                "",
                "",
                selectedIndex,
                0.78,
                "用户输入承接上一轮候选并表达了后续动作。"
        );
    }

    private IntentRecognitionResponse directAnswerResponse(
            String message,
            AssistantConversationState conversation,
            ConversationContextResolution resolution
    ) {
        AssistantConversationFocus focus = conversation.focus();
        CandidateItem candidate = candidateForResolution(focus, resolution);
        Map<String, Object> entities = candidate == null
                ? Map.of()
                : Map.of(
                "target_name", candidate.name()
        );

        return new IntentRecognitionResponse(
                UUID.randomUUID().toString(),
                SCHEMA_VERSION,
                settings.directAnswer().templateId(),
                settings.directAnswer().provider(),
                settings.directAnswer().model(),
                message,
                settings.directAnswer().id(),
                settings.directAnswer().name(),
                settings.directAnswer().taskType(),
                resolution.confidence(),
                message,
                resolution.rewrittenMessage().isBlank() ? message : resolution.rewrittenMessage(),
                entities,
                List.of(),
                List.of(),
                "respond_only",
                new SafetyDecision("none", false, false, "当前只回答上一轮文件上下文，不执行真实文件操作。"),
                new ActionDraft("none", Map.of(), false),
                BackendActionDraft.skipped("not_requested", "上下文追问不生成后端请求草稿。"),
                ActionPlan.skipped("completed", "已回答上一轮文件上下文追问。"),
                resolution.answerText(),
                "",
                resolution.reason(),
                "",
                CandidateBindingResult.skipped("not_requested", "上下文追问不触发新的候选绑定。"),
                null
        );
    }

    private IntentRecognitionResponse clarificationResponse(
            String message,
            AssistantConversationState conversation,
            ConversationContextResolution resolution
    ) {
        return new IntentRecognitionResponse(
                UUID.randomUUID().toString(),
                SCHEMA_VERSION,
                settings.directAnswer().templateId(),
                settings.directAnswer().provider(),
                settings.directAnswer().model(),
                message,
                "fallback",
                "上下文澄清",
                "fallback",
                resolution.confidence(),
                message,
                message,
                Map.of(),
                List.of("operation"),
                List.of("operation"),
                "ask_clarification",
                new SafetyDecision("none", false, false, "上下文指代不明确，不能执行或猜测。"),
                new ActionDraft("none", Map.of(), false),
                BackendActionDraft.skipped("not_requested", "上下文不明确，未生成后端请求草稿。"),
                ActionPlan.skipped("clarification_required", resolution.clarificationQuestion()),
                resolution.clarificationQuestion(),
                resolution.clarificationQuestion(),
                resolution.reason(),
                "",
                CandidateBindingResult.skipped("not_requested", "上下文澄清不触发新的候选绑定。"),
                null
        );
    }

    private ConversationContextResolution resolutionFromModel(Map<String, Object> payload) {
        return new ConversationContextResolution(
                stringValue(payload, "relation", "new_task"),
                stringValue(payload, "referent", "none"),
                stringValue(payload, "context_action", ""),
                stringValue(payload, "rewritten_message", ""),
                objectMap(payload.get("carried_entities")),
                stringList(payload.get("required_client_fields")),
                stringValue(payload, "answer_text", ""),
                stringValue(payload, "clarification_question", ""),
                doubleValue(payload, "confidence", 0.0),
                stringValue(payload, "reason", "")
        );
    }

    private boolean referencesContext(String message) {
        return containsAny(message, settings.referenceTerms())
                || containsAny(message, settings.setReferenceTerms())
                || parseSelectionIndex(message).isPresent();
    }

    private boolean asksFollowUpQuestion(String message) {
        return containsAny(message, settings.followUpQuestionMarkers());
    }

    private Optional<QuestionField> questionField(String message) {
        return settings.questionFields().stream()
                .filter(field -> containsAny(message, field.aliases()))
                .findFirst();
    }

    private String mutationAction(String message) {
        for (Map.Entry<String, List<String>> entry : settings.mutationMarkers().entrySet()) {
            if (containsAny(message, entry.getValue())) {
                return entry.getKey();
            }
        }
        return "";
    }

    private CandidateSelection selectedReference(String message, AssistantConversationFocus focus) {
        Optional<Integer> index = parseSelectionIndex(message);
        if (index.isEmpty() || focus == null || focus.candidateBinding() == null) {
            return CandidateSelection.notSelected();
        }

        CandidateBindingResult selectedBinding = focus.candidateBinding().select(index.get() - 1);
        if (!"selected_candidate".equals(selectedBinding.status())) {
            return CandidateSelection.outOfRange(selectedBinding.message());
        }
        return CandidateSelection.selected(selectedBinding.selectedCandidate(), selectedBinding.selectedIndex());
    }

    private Optional<Integer> parseSelectionIndex(String message) {
        String normalized = normalize(message);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        return settings.ordinalAliases().entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()))
                .filter(entry -> isSelectionAliasMatch(normalized, entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && value > 0)
                .findFirst();
    }

    private boolean isSelectionAliasMatch(String message, String alias) {
        if (alias == null || alias.isBlank()) {
            return false;
        }
        if (alias.length() > 1) {
            return message.contains(alias);
        }
        return message.contains("第" + alias)
                || message.contains(alias + "个")
                || message.contains(alias + "项")
                || message.contains(alias + "条")
                || message.contains(alias + "号");
    }

    private CandidateItem candidateForResolution(
            AssistantConversationFocus focus,
            ConversationContextResolution resolution
    ) {
        if (focus == null) {
            return null;
        }
        Integer selectedIndex = resolution == null ? null : resolution.selectedIndex();
        if (selectedIndex != null && focus.candidateBinding() != null) {
            CandidateBindingResult selected = focus.candidateBinding().select(selectedIndex - 1);
            if ("selected_candidate".equals(selected.status())) {
                return selected.selectedCandidate();
            }
        }
        return focus.effectiveCandidate();
    }

    private String answerFor(CandidateItem candidate, QuestionField field) {
        if (candidate == null) {
            return settings.message("missing_context");
        }
        if ("file_format".equals(field.id())) {
            if ("FOLDER".equalsIgnoreCase(candidate.type())) {
                return formatTemplate(field.folderTemplate(), candidate, "文件夹");
            }
            String format = fileFormat(candidate);
            if (format.isBlank()) {
                return formatTemplate(field.unknownTemplate(), candidate, "");
            }
            return formatTemplate(field.answerTemplate(), candidate, format);
        }
        String value = switch (field.id()) {
            case "file_name" -> candidate.name();
            case "file_path" -> candidate.path();
            case "file_size" -> formatSize(candidate.size());
            case "updated_at" -> candidate.updatedAt();
            default -> "";
        };
        if (value.isBlank() && !field.unknownTemplate().isBlank()) {
            return formatTemplate(field.unknownTemplate(), candidate, "");
        }
        return formatTemplate(field.answerTemplate(), candidate, value);
    }

    private String fileFormat(CandidateItem candidate) {
        String extension = candidate.extension();
        if (extension.isBlank()) {
            extension = extensionFromName(candidate.name());
        }
        if (!extension.isBlank()) {
            return extension.toUpperCase(Locale.ROOT);
        }
        String mimeType = candidate.mimeType();
        if (mimeType.contains("/")) {
            String subtype = mimeType.substring(mimeType.indexOf('/') + 1).trim();
            return subtype.toUpperCase(Locale.ROOT);
        }
        return "";
    }

    private String extensionFromName(String name) {
        String value = name == null ? "" : name.trim();
        int index = value.lastIndexOf('.');
        if (index < 0 || index == value.length() - 1) {
            return "";
        }
        return value.substring(index + 1);
    }

    private String formatSize(Long size) {
        if (size == null || size < 0) {
            return "";
        }
        if (size < 1024L) {
            return size + " B";
        }
        double value = size;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value = value / 1024.0;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private String formatTemplate(String template, CandidateItem candidate, String value) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", candidate.name());
        values.put("value", value == null ? "" : value);
        return TextSupport.safeFormat(template, values);
    }

    private boolean containsAny(String message, List<String> values) {
        for (String value : values) {
            if (!value.isBlank() && message.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String message) {
        return message == null ? "" : message.trim().replaceAll("\\s+", "");
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String stringValue(Map<String, Object> payload, String key, String fallback) {
        Object value = payload.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
    }

    private double doubleValue(Map<String, Object> payload, String key, double fallback) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public record ContextAttempt(
            boolean applied,
            IntentRecognitionResponse response,
            ConversationContextResolution resolution
    ) {
        public static ContextAttempt notApplied() {
            return new ContextAttempt(false, null, null);
        }

        public static ContextAttempt answer(IntentRecognitionResponse response) {
            return new ContextAttempt(true, response, null);
        }

        public static ContextAttempt rewritten(
                IntentRecognitionResponse response,
                ConversationContextResolution resolution
        ) {
            return new ContextAttempt(true, response, resolution);
        }
    }

    private record CandidateSelection(
            CandidateItem selectedCandidate,
            Integer oneBasedIndex,
            String message,
            boolean outOfRange
    ) {
        static CandidateSelection notSelected() {
            return new CandidateSelection(null, null, "", false);
        }

        static CandidateSelection selected(CandidateItem candidate, Integer oneBasedIndex) {
            return new CandidateSelection(candidate, oneBasedIndex, "", false);
        }

        static CandidateSelection outOfRange(String message) {
            return new CandidateSelection(null, null, message, true);
        }
    }

    private record Settings(
            boolean enabled,
            double minConfidence,
            DirectAnswerIntent directAnswer,
            List<QuestionField> questionFields,
            Map<String, String> messages,
            List<String> referenceTerms,
            List<String> setReferenceTerms,
            List<String> clientInputActionTypes,
            List<String> followUpQuestionMarkers,
            Map<String, List<String>> mutationMarkers,
            Map<String, Integer> ordinalAliases
    ) {
        static Settings load(JsonNode root, JsonNode candidateSelection) {
            JsonNode localFallback = root.path("local_fallback");
            return new Settings(
                    root.path("enabled").asBoolean(true),
                    root.path("min_confidence").asDouble(0.62),
                    directAnswer(root.path("direct_answer_intent")),
                    questionFields(root.path("question_fields")),
                    stringMap(root.path("messages")),
                    stringList(localFallback.path("reference_terms")),
                    stringList(localFallback.path("set_reference_terms")),
                    stringList(localFallback.path("client_input_action_types")),
                    stringList(localFallback.path("follow_up_question_markers")),
                    stringListMap(localFallback.path("mutation_markers")),
                    ordinalAliases(candidateSelection.path("ordinal_aliases"))
            );
        }

        String message(String key) {
            return messages.getOrDefault(key, "");
        }

        QuestionField defaultQuestionField() {
            return questionFields.stream()
                    .filter(field -> "file_name".equals(field.id()))
                    .findFirst()
                    .orElse(questionFields.getFirst());
        }

        Optional<QuestionField> questionField(String id) {
            if (id == null || id.isBlank()) {
                return Optional.empty();
            }
            return questionFields.stream()
                    .filter(field -> id.equals(field.id()))
                    .findFirst();
        }

        private static DirectAnswerIntent directAnswer(JsonNode node) {
            return new DirectAnswerIntent(
                    node.path("id").asText("assistant_file_context_question"),
                    node.path("name").asText("文件上下文追问"),
                    node.path("task_type").asText("file_context_query"),
                    node.path("template_id").asText("contextual_file_question"),
                    node.path("provider").asText("local_context"),
                    node.path("model").asText("conversation_focus")
            );
        }

        private static List<QuestionField> questionFields(JsonNode node) {
            if (!node.isObject()) {
                return List.of(new QuestionField(
                        "file_name",
                        List.of("名字", "名称"),
                        "它的名称是 {name}。",
                        "",
                        ""
                ));
            }
            List<QuestionField> fields = new ArrayList<>();
            node.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                fields.add(new QuestionField(
                        entry.getKey(),
                        stringList(value.path("aliases")),
                        value.path("answer_template").asText(""),
                        value.path("folder_template").asText(""),
                        value.path("unknown_template").asText("")
                ));
            });
            return List.copyOf(fields);
        }

        private static Map<String, String> stringMap(JsonNode node) {
            if (!node.isObject()) {
                return Map.of();
            }
            Map<String, String> values = new LinkedHashMap<>();
            node.properties().forEach(entry -> values.put(entry.getKey(), entry.getValue().asText("")));
            return Map.copyOf(values);
        }

        private static Map<String, List<String>> stringListMap(JsonNode node) {
            if (!node.isObject()) {
                return Map.of();
            }
            Map<String, List<String>> values = new LinkedHashMap<>();
            node.properties().forEach(entry -> values.put(entry.getKey(), stringList(entry.getValue())));
            return Map.copyOf(values);
        }

        private static Map<String, Integer> ordinalAliases(JsonNode node) {
            if (!node.isObject()) {
                return Map.of();
            }
            Map<String, Integer> values = new LinkedHashMap<>();
            node.properties().forEach(entry -> {
                String key = entry.getKey() == null ? "" : entry.getKey().trim().replaceAll("\\s+", "");
                int value = entry.getValue().asInt(0);
                if (!key.isBlank() && value > 0) {
                    values.put(key, value);
                }
            });
            return Map.copyOf(values);
        }

        private static List<String> stringList(JsonNode node) {
            if (!node.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            node.forEach(item -> {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            });
            return List.copyOf(values);
        }
    }

    private record DirectAnswerIntent(
            String id,
            String name,
            String taskType,
            String templateId,
            String provider,
            String model
    ) {
    }

    private record QuestionField(
            String id,
            List<String> aliases,
            String answerTemplate,
            String folderTemplate,
            String unknownTemplate
    ) {
    }
}
