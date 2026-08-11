package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IntentRecognitionService {

    private static final String SCHEMA_VERSION = "intent_recognition_v1";

    private final IntentModelClient modelClient;
    private final IntentRouter intentRouter;
    private final List<ResponseTemplate> responseTemplates;
    private final Map<String, String> personaPlaceholders;

    public IntentRecognitionService(
            IntentModelClient modelClient,
            IntentRouter intentRouter,
            RagConfigLoader configLoader
    ) {
        this.modelClient = modelClient;
        this.intentRouter = intentRouter;
        this.responseTemplates = loadResponseTemplates(configLoader);
        this.personaPlaceholders = loadPersonaPlaceholders(configLoader);
    }

    public IntentRecognitionResponse recognize(String message) {
        return modelClient.recognize(message)
                .map(result -> fromModel(message, result))
                .orElseGet(() -> fromFallback(message, "DeepSeek 未配置或暂时不可用"));
    }

    private IntentRecognitionResponse fromModel(String message, IntentModelClient.ModelIntentResult result) {
        Map<String, Object> payload = result.payload();
        String intentId = stringValue(payload, "intent_id", stringValue(payload, "intent", "fallback"));
        IntentRouter.IntentDefinition intent = validIntent(intentId);
        Map<String, Object> entities = sanitizeSlotMap(objectMap(payload.get("entities")), intent.allowedSlots());
        List<String> requiredSlots = intent.requiredSlots();
        List<String> modelMissingSlots = normalizeAllowedSlots(stringList(payload.get("missing_slots")), intent.allowedSlots());
        List<String> missingSlots = missingSlots(requiredSlots, entities);
        String rawNextAction = stringValue(payload, "next_action", "");
        String nextAction = normalizeNextAction(intent, rawNextAction, missingSlots);
        String risk = normalizeRisk(intent.risk());
        boolean requiresConfirmation = intent.requiresConfirmation();
        String assistantText = assistantText(payload, intent, nextAction, missingSlots, modelMissingSlots);
        String clarificationQuestion = clarificationQuestion(intent, missingSlots);

        return new IntentRecognitionResponse(
                UUID.randomUUID().toString(),
                SCHEMA_VERSION,
                result.templateId(),
                result.provider(),
                result.model(),
                message,
                intent.id(),
                stringValue(payload, "intent_name", intent.name()),
                stringValue(payload, "task_type", intent.taskType()),
                confidence(payload),
                stringValue(payload, "user_goal", message),
                stringValue(payload, "normalized_query", message),
                entities,
                requiredSlots,
                missingSlots,
                nextAction,
                new SafetyDecision(
                        risk,
                        requiresConfirmation,
                        false,
                        safetyReason(intent.actionType(), risk)
                ),
                actionDraft(payload.get("action_draft"), intent, entities),
                BackendActionDraft.skipped("not_requested", "用户尚未确认，未生成后端请求草稿。"),
                ActionPlan.skipped("understanding", "ActionPlan 尚未生成。"),
                assistantText,
                clarificationQuestion,
                stringValue(payload, "reason", ""),
                "",
                CandidateBindingResult.skipped("not_requested", "候选绑定尚未执行。"),
                null
        );
    }

    public IntentRecognitionResponse rebuildForConversation(
            IntentRecognitionResponse baseResponse,
            String intentId,
            Map<String, Object> mergedEntities,
            String conversationReason
    ) {
        IntentRouter.IntentDefinition intent = validIntent(intentId);
        Map<String, Object> entities = sanitizeSlotMap(mergedEntities, intent.allowedSlots());
        List<String> missingSlots = missingSlots(intent.requiredSlots(), entities);
        String nextAction = normalizeNextAction(intent, baseResponse.nextAction(), missingSlots);
        String risk = normalizeRisk(intent.risk());
        String reason = conversationReason == null || conversationReason.isBlank()
                ? baseResponse.reason()
                : conversationReason;

        return new IntentRecognitionResponse(
                baseResponse.id(),
                SCHEMA_VERSION,
                baseResponse.templateId(),
                baseResponse.provider(),
                baseResponse.model(),
                baseResponse.message(),
                intent.id(),
                intent.name(),
                intent.taskType(),
                baseResponse.confidence(),
                baseResponse.userGoal(),
                baseResponse.normalizedQuery(),
                entities,
                intent.requiredSlots(),
                missingSlots,
                nextAction,
                new SafetyDecision(
                        risk,
                        intent.requiresConfirmation(),
                        false,
                        safetyReason(intent.actionType(), risk)
                ),
                fallbackActionDraft(intent, entities),
                BackendActionDraft.skipped("not_requested", "用户尚未确认，未生成后端请求草稿。"),
                ActionPlan.skipped("understanding", "ActionPlan 尚未生成。"),
                renderTemplate(intent.id(), intent.name(), nextAction, missingSlots),
                clarificationQuestion(intent, missingSlots),
                reason,
                baseResponse.fallbackReason(),
                CandidateBindingResult.skipped("not_requested", "候选绑定尚未执行。"),
                null
        );
    }

    public IntentRecognitionResponse withFlowState(
            IntentRecognitionResponse response,
            String nextAction,
            String reason
    ) {
        if (response == null) {
            return null;
        }
        String safeNextAction = intentRouter.isAllowedNextAction(nextAction) ? nextAction : response.nextAction();
        String safeReason = reason == null || reason.isBlank() ? response.reason() : reason;
        return new IntentRecognitionResponse(
                response.id(),
                response.schemaVersion(),
                response.templateId(),
                response.provider(),
                response.model(),
                response.message(),
                response.intentId(),
                response.intentName(),
                response.taskType(),
                response.confidence(),
                response.userGoal(),
                response.normalizedQuery(),
                response.entities(),
                response.requiredSlots(),
                response.missingSlots(),
                safeNextAction,
                response.safety(),
                response.actionDraft(),
                response.backendActionDraft(),
                response.actionPlan(),
                renderTemplate(response.intentId(), response.intentName(), safeNextAction, response.missingSlots()),
                response.clarificationQuestion(),
                safeReason,
                response.fallbackReason(),
                response.candidateBinding(),
                response.conversation()
        );
    }

    private IntentRecognitionResponse fromFallback(String message, String fallbackReason) {
        IntentRouter.IntentRouteResult route = intentRouter.route(message);
        IntentRouter.IntentDefinition intent = intentRouter.getIntent(route.intent());
        Map<String, Object> entities = sanitizeSlotMap(new LinkedHashMap<>(route.entities()), intent.allowedSlots());
        List<String> missingSlots = missingSlots(intent.requiredSlots(), entities);
        String nextAction = normalizeNextAction(intent, route.nextAction(), missingSlots);
        String assistantText = renderTemplate(intent.id(), intent.name(), nextAction, missingSlots);
        String risk = normalizeRisk(intent.risk());

        return new IntentRecognitionResponse(
                UUID.randomUUID().toString(),
                SCHEMA_VERSION,
                "local_fallback_intent_template",
                "local_fallback",
                "",
                message,
                intent.id(),
                intent.name(),
                intent.taskType(),
                route.confidence(),
                message,
                message,
                entities,
                intent.requiredSlots(),
                missingSlots,
                nextAction,
                new SafetyDecision(risk, intent.requiresConfirmation(), false, safetyReason(intent.actionType(), risk)),
                fallbackActionDraft(intent, entities),
                BackendActionDraft.skipped("not_requested", "用户尚未确认，未生成后端请求草稿。"),
                ActionPlan.skipped("understanding", "ActionPlan 尚未生成。"),
                assistantText,
                clarificationQuestion(intent, missingSlots),
                route.reason(),
                fallbackReason,
                CandidateBindingResult.skipped("not_requested", "候选绑定尚未执行。"),
                null
        );
    }

    private IntentRouter.IntentDefinition validIntent(String intentId) {
        return intentRouter.hasIntent(intentId) ? intentRouter.getIntent(intentId) : intentRouter.getIntent("fallback");
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> source)) {
            return List.of();
        }
        return source.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private List<String> missingSlots(List<String> requiredSlots, Map<String, Object> entities) {
        return requiredSlots.stream()
                .filter(slot -> !entities.containsKey(slot) || String.valueOf(entities.get(slot)).isBlank())
                .toList();
    }

    private ActionDraft actionDraft(Object value, IntentRouter.IntentDefinition intent, Map<String, Object> entities) {
        Map<String, Object> draft = objectMap(value);
        String type = normalizeActionType(intent.actionType());
        Map<String, Object> parameters = new LinkedHashMap<>(entities);
        parameters.putAll(sanitizeSlotMap(objectMap(draft.get("parameters")), intent.allowedSlots()));
        return new ActionDraft(type, parameters, !"none".equals(type));
    }

    private ActionDraft fallbackActionDraft(IntentRouter.IntentDefinition intent, Map<String, Object> entities) {
        String type = normalizeActionType(intent.actionType());
        return new ActionDraft(
                type,
                new LinkedHashMap<>(entities),
                !"none".equals(type)
        );
    }

    private Map<String, Object> sanitizeSlotMap(Map<String, Object> source, List<String> allowedSlots) {
        if (source.isEmpty()) {
            return Map.of();
        }

        List<String> allowed = allowedSlots == null ? List.of() : allowedSlots;
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalizedKey = intentRouter.normalizeSlotId(key);
            if (normalizedKey.isBlank()
                    || !allowed.contains(normalizedKey)
                    || intentRouter.isForbiddenActionParameterKey(normalizedKey)) {
                return;
            }

            Object sanitized = sanitizeParameterValue(value);
            if (hasParameterValue(sanitized)) {
                result.put(normalizedKey, sanitized);
            }
        });
        return result;
    }

    private Object sanitizeParameterValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> result = new LinkedHashMap<>();
            mapValue.forEach((key, item) -> {
                String normalizedKey = String.valueOf(key).trim();
                if (normalizedKey.isBlank() || intentRouter.isForbiddenActionParameterKey(normalizedKey)) {
                    return;
                }
                Object sanitized = sanitizeParameterValue(item);
                if (hasParameterValue(sanitized)) {
                    result.put(normalizedKey, sanitized);
                }
            });
            return result;
        }

        if (value instanceof List<?> listValue) {
            return listValue.stream()
                    .map(this::sanitizeParameterValue)
                    .filter(this::hasParameterValue)
                    .toList();
        }

        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }

        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean hasParameterValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String stringValue) {
            return !stringValue.isBlank();
        }
        if (value instanceof Map<?, ?> mapValue) {
            return !mapValue.isEmpty();
        }
        if (value instanceof List<?> listValue) {
            return !listValue.isEmpty();
        }
        return true;
    }

    private List<String> normalizeAllowedSlots(List<String> slots, List<String> allowedSlots) {
        return slots.stream()
                .map(intentRouter::normalizeSlotId)
                .filter(slot -> !slot.isBlank())
                .filter(slot -> allowedSlots.contains(slot))
                .distinct()
                .toList();
    }

    private String normalizeNextAction(
            IntentRouter.IntentDefinition intent,
            String requestedNextAction,
            List<String> missingSlots
    ) {
        if ("fallback".equals(intent.id()) || !missingSlots.isEmpty()) {
            return "ask_clarification";
        }

        if (!intentRouter.isAllowedNextAction(requestedNextAction)
                || "ask_clarification".equals(requestedNextAction)
                || "wait_for_user_confirmation".equals(requestedNextAction)) {
            return "wait_for_backend_binding";
        }

        return requestedNextAction;
    }

    private String normalizeRisk(String risk) {
        return intentRouter.isAllowedRisk(risk) ? risk : "none";
    }

    private String normalizeActionType(String actionType) {
        String normalized = actionType == null || actionType.isBlank() ? "none" : actionType;
        return intentRouter.isAllowedActionType(normalized) ? normalized : "none";
    }

    private double confidence(Map<String, Object> payload) {
        double value = doubleValue(payload, "confidence", 0.0);
        if (value < 0.0) {
            return 0.0;
        }
        return Math.min(value, 1.0);
    }

    private String assistantText(
            Map<String, Object> payload,
            IntentRouter.IntentDefinition intent,
            String nextAction,
            List<String> missingSlots,
            List<String> modelMissingSlots
    ) {
        String rendered = renderTemplate(intent.id(), intent.name(), nextAction, missingSlots);
        if ("fallback".equals(intent.id())) {
            return rendered;
        }
        String modelText = stringValue(payload, "assistant_text", "");
        if (modelText.isBlank() || !sameSlots(modelMissingSlots, missingSlots)) {
            return rendered;
        }
        return modelText;
    }

    private boolean sameSlots(List<String> left, List<String> right) {
        return new LinkedHashSet<>(left).equals(new LinkedHashSet<>(right));
    }

    private String clarificationQuestion(IntentRouter.IntentDefinition intent, List<String> missingSlots) {
        if (missingSlots.isEmpty()) {
            return "";
        }
        if (intent.clarificationQuestion() != null && !intent.clarificationQuestion().isBlank()) {
            return intent.clarificationQuestion();
        }
        List<String> clarifications = missingSlots.stream()
                .map(intentRouter::slotClarification)
                .filter(item -> item != null && !item.isBlank())
                .toList();
        if (!clarifications.isEmpty()) {
            return String.join(" ", clarifications);
        }
        return "请补充：" + missingSlotLabels(missingSlots);
    }

    private String renderTemplate(String intentId, String intentName, String nextAction, List<String> missingSlots) {
        String condition = nextAction == null || nextAction.isBlank() ? "*" : nextAction;
        ResponseTemplate template = responseTemplates.stream()
                .filter(item -> item.matches(intentId, condition))
                .findFirst()
                .orElse(responseTemplates.getLast());
        Map<String, String> values = new LinkedHashMap<>(personaPlaceholders);
        values.put("intent_name", intentName);
        values.put("next_action", nextAction);
        values.put("missing_slots_text", missingSlots.isEmpty() ? "无" : missingSlotLabels(missingSlots));
        return TextSupport.safeFormat(template.messageTemplate(), values);
    }

    private String missingSlotLabels(List<String> missingSlots) {
        return missingSlots.stream()
                .map(intentRouter::slotLabel)
                .toList()
                .stream()
                .collect(java.util.stream.Collectors.joining("、"));
    }

    private List<ResponseTemplate> loadResponseTemplates(RagConfigLoader configLoader) {
        List<Map<String, String>> rows = configLoader.loadCsv("rag/conversation/response_templates.csv");
        List<ResponseTemplate> templates = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Map<String, String> row = rows.get(index);
            if (row.getOrDefault("template_id", "").isBlank()) {
                continue;
            }
            templates.add(new ResponseTemplate(
                    row.get("template_id"),
                    parseBool(row.getOrDefault("enabled", "true")),
                    parseInt(row.get("priority")),
                    row.getOrDefault("intent_id", "*"),
                    row.getOrDefault("message_template", ""),
                    row.getOrDefault("next_action", "*"),
                    index + 2
            ));
        }
        templates.sort(Comparator.comparingInt(ResponseTemplate::priority).reversed().thenComparingInt(ResponseTemplate::rowNumber));
        return List.copyOf(templates);
    }

    private Map<String, String> loadPersonaPlaceholders(RagConfigLoader configLoader) {
        JsonNode persona = configLoader.loadJson("rag/conversation/persona.json");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("persona_id", persona.path("id").asText("anan"));
        values.put("persona_name", persona.path("displayName").asText("安安"));
        values.put("persona_role", persona.path("role").asText("Alicia 云盘的文件管家"));
        values.put("persona_tone", persona.path("tone").asText(""));
        values.put("persona_identity", persona.path("identitySummary").asText(""));
        values.put("persona_user_identity_reply", persona.path("userIdentityReply").asText(""));
        values.put("persona_social_reply", persona.path("socialReply").asText(""));
        values.put("persona_chat_reply", persona.path("chatReply").asText(""));
        values.put("persona_bored_reply", persona.path("boredReply").asText(""));
        values.put("persona_writing_help_reply", persona.path("writingHelpReply").asText(""));
        values.put("persona_external_resource_reply", persona.path("externalResourceReply").asText(""));
        values.put("persona_errand_unsupported_reply", persona.path("errandUnsupportedReply").asText(""));
        values.put("persona_product_info_reply", persona.path("productInfoReply").asText(""));
        values.put("persona_product_reason_reply", persona.path("productReasonReply").asText(""));
        values.put("persona_product_feedback_reply", persona.path("productFeedbackReply").asText(""));
        return Map.copyOf(values);
    }

    private String stringValue(Map<String, Object> payload, String key, String defaultValue) {
        Object value = payload.get(key);
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private double doubleValue(Map<String, Object> payload, String key, double defaultValue) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private boolean parseBool(String value) {
        return value != null && List.of("1", "true", "yes", "y").contains(value.trim().toLowerCase());
    }

    private int parseInt(String value) {
        return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
    }

    private String safetyReason(String actionType, String risk) {
        if ("none".equals(actionType) || "search".equals(actionType)) {
            return "当前只输出意图模板，不执行真实文件操作。";
        }
        return "真实文件操作必须交给后端权限校验和用户确认流程，RAG 不直接执行。风险等级：" + risk;
    }

    private record ResponseTemplate(
            String templateId,
            boolean enabled,
            int priority,
            String intentId,
            String messageTemplate,
            String nextAction,
            int rowNumber
    ) {
        boolean matches(String actualIntentId, String actualNextAction) {
            return enabled
                    && ("*".equals(intentId) || intentId.equals(actualIntentId))
                    && ("*".equals(nextAction) || nextAction.equals(actualNextAction));
        }
    }
}
