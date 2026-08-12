package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final AssistantReplyPolisher replyPolisher;
    private final SemanticFrameResolver semanticFrameResolver;
    private final List<ResponseTemplate> responseTemplates;
    private final Map<String, String> personaPlaceholders;

    @Autowired
    public IntentRecognitionService(
            IntentModelClient modelClient,
            IntentRouter intentRouter,
            RagConfigLoader configLoader,
            AssistantReplyPolisher replyPolisher,
            FileQueryPlanResolver fileQueryPlanResolver,
            SemanticFrameResolver semanticFrameResolver
    ) {
        this.modelClient = modelClient;
        this.intentRouter = intentRouter;
        this.replyPolisher = replyPolisher == null ? AssistantReplyPolisher.noop() : replyPolisher;
        this.semanticFrameResolver = semanticFrameResolver == null
                ? new SemanticFrameResolver()
                : semanticFrameResolver;
        this.responseTemplates = loadResponseTemplates(configLoader);
        this.personaPlaceholders = loadPersonaPlaceholders(configLoader);
    }

    public IntentRecognitionService(
            IntentModelClient modelClient,
            IntentRouter intentRouter,
            RagConfigLoader configLoader,
            AssistantReplyPolisher replyPolisher,
            FileQueryPlanResolver fileQueryPlanResolver
    ) {
        this(modelClient, intentRouter, configLoader, replyPolisher, fileQueryPlanResolver, new SemanticFrameResolver());
    }

    public IntentRecognitionService(
            IntentModelClient modelClient,
            IntentRouter intentRouter,
            RagConfigLoader configLoader,
            AssistantReplyPolisher replyPolisher
    ) {
        this(modelClient, intentRouter, configLoader, replyPolisher, FileQueryPlanResolver.defaults());
    }

    public IntentRecognitionService(
            IntentModelClient modelClient,
            IntentRouter intentRouter,
            RagConfigLoader configLoader
    ) {
        this(modelClient, intentRouter, configLoader, AssistantReplyPolisher.noop());
    }

    public IntentRecognitionResponse recognize(String message) {
        return recognize(message, null, AssistantClientContext.empty());
    }

    public IntentRecognitionResponse recognize(
            String message,
            AssistantConversationState conversation,
            AssistantClientContext clientContext
    ) {
        AssistantClientContext safeClientContext = clientContext == null
                ? AssistantClientContext.empty()
                : clientContext;
        IntentModelClient.ModelIntentResult modelResult = modelClient.recognize(
                        new IntentModelClient.IntentModelRequest(
                                message,
                                semanticContext(conversation, safeClientContext)
                        )
                )
                .orElse(null);
        IntentRecognitionResponse response = modelResult == null
                ? fromFallback(message, "DeepSeek 未配置或暂时不可用")
                : fromModel(message, modelResult);
        IntentRouter.IntentRouteResult localRoute = intentRouter.route(message);
        boolean guardedByLocalRoute = shouldUseLocalRouteGuard(response, localRoute);
        if (guardedByLocalRoute) {
            response = fromFallback(message, "DeepSeek 未可靠命中，已由高置信配置规则复核。");
        }
        Map<String, Object> modelPayload = modelResult == null || guardedByLocalRoute
                ? Map.of()
                : modelResult.payload();
        SemanticFrame semanticFrame = semanticFrameResolver.resolve(
                message,
                response,
                conversation,
                safeClientContext,
                modelPayload
        );

        if (semanticFrameResolver.shouldReusePreviousIntent(semanticFrame, response, conversation)) {
            response = rebuildForConversation(
                    response,
                    conversation.pendingIntentId(),
                    semanticFrameResolver.entitiesForFrame(response, semanticFrame),
                    "用户正在修正或补充上一轮语义。"
            );
        }

        Map<String, Object> entities = semanticFrameResolver.entitiesForFrame(response, semanticFrame);
        ActionDraft actionDraft = semanticFrameResolver.actionDraftFor(response, semanticFrame, entities);
        response = response.withSemanticFrame(semanticFrame, entities, actionDraft);
        if (semanticFrame.needsClarification()) {
            response = response.withSemanticClarification(semanticFrame);
        }
        return response;
    }

    private boolean shouldUseLocalRouteGuard(
            IntentRecognitionResponse modelResponse,
            IntentRouter.IntentRouteResult localRoute
    ) {
        if (modelResponse == null || localRoute == null || "fallback".equals(localRoute.intent())) {
            return false;
        }
        boolean modelUncertain = "fallback".equals(modelResponse.intentId()) || modelResponse.confidence() < 0.65;
        return modelUncertain && localRoute.confidence() >= 0.9;
    }

    private Map<String, Object> semanticContext(
            AssistantConversationState conversation,
            AssistantClientContext clientContext
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (conversation != null) {
            context.put("conversation_id", conversation.conversationId());
            context.put("turn_index", conversation.turnIndex());
            context.put("pending_intent_id", conversation.pendingIntentId());
            context.put("pending_entities", conversation.entities());
            context.put("pending_slots", conversation.pendingSlots());
            AssistantConversationFocus focus = conversation.focus();
            if (focus != null) {
                context.put("focus_kind", focus.focusKind());
                context.put("focus_action_type", focus.actionType());
                context.put("focus_entities", focus.entities());
                context.put("selected_candidate", candidateContext(focus.effectiveCandidate()));
                context.put("candidates", focus.candidateBinding() == null
                        ? List.of()
                        : focus.candidateBinding().candidates().stream()
                        .map(this::candidateContext)
                        .toList());
            }
        }
        context.put("client_context", Map.of(
                "current_folder_id", clientContext.currentFolderId() == null ? "" : clientContext.currentFolderId(),
                "current_folder_path", clientContext.currentFolderPath(),
                "available_client_inputs", clientContext.availableClientInputs()
        ));
        return Map.copyOf(context);
    }

    private Map<String, Object> candidateContext(CandidateItem candidate) {
        if (candidate == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", candidate.name());
        result.put("type", candidate.type());
        result.put("path", candidate.path());
        result.put("extension", candidate.extension());
        result.put("updated_at", candidate.updatedAt());
        if (candidate.size() != null) {
            result.put("size", candidate.size());
        }
        return Map.copyOf(result);
    }

    private IntentRecognitionResponse fromModel(String message, IntentModelClient.ModelIntentResult result) {
        Map<String, Object> payload = result.payload();
        String intentId = stringValue(payload, "intent_id", stringValue(payload, "intent", "fallback"));
        IntentRouter.IntentDefinition intent = validIntent(intentId);
        Map<String, Object> entities = sanitizeSlotMap(objectMap(payload.get("entities")), intent.allowedSlots());
        List<String> requiredSlots = intent.requiredSlots();
        List<String> modelMissingSlots = normalizeAllowedSlots(stringList(payload.get("missing_slots")), intent.allowedSlots());
        List<String> missingSlots = missingSlots(requiredSlots, entities);
        String nextAction = normalizeNextAction(intent, missingSlots);
        String risk = normalizeRisk(intent.risk());
        boolean requiresConfirmation = intent.requiresConfirmation();
        String assistantText = assistantText(message, payload, intent, nextAction, missingSlots, modelMissingSlots);
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
        String nextAction = normalizeNextAction(intent, missingSlots);
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
                templateText(baseResponse.message(), intent, nextAction, missingSlots),
                clarificationQuestion(intent, missingSlots),
                reason,
                baseResponse.fallbackReason(),
                CandidateBindingResult.skipped("not_requested", "候选绑定尚未执行。"),
                null,
                baseResponse.semanticFrame(),
                baseResponse.interaction()
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
        IntentRouter.IntentDefinition intent = validIntent(response.intentId());
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
                templateText(response.message(), intent, safeNextAction, response.missingSlots()),
                response.clarificationQuestion(),
                safeReason,
                response.fallbackReason(),
                response.candidateBinding(),
                response.conversation(),
                response.semanticFrame(),
                response.interaction()
        );
    }

    private IntentRecognitionResponse fromFallback(String message, String fallbackReason) {
        IntentRouter.IntentRouteResult route = intentRouter.route(message);
        IntentRouter.IntentDefinition intent = intentRouter.getIntent(route.intent());
        Map<String, Object> entities = sanitizeSlotMap(new LinkedHashMap<>(route.entities()), intent.allowedSlots());
        List<String> missingSlots = missingSlots(intent.requiredSlots(), entities);
        String nextAction = normalizeNextAction(intent, missingSlots);
        String assistantText = templateText(message, intent, nextAction, missingSlots);
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
            List<String> missingSlots
    ) {
        if ("fallback".equals(intent.id()) || !missingSlots.isEmpty()) {
            return "ask_clarification";
        }

        String configuredNextAction = intent.nextAction();
        if (intentRouter.isAllowedNextAction(configuredNextAction)
                && !"ask_clarification".equals(configuredNextAction)
                && !"wait_for_user_confirmation".equals(configuredNextAction)) {
            return configuredNextAction;
        }

        return "none".equals(intent.actionType()) ? "respond_only" : "wait_for_backend_binding";
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
            String message,
            Map<String, Object> payload,
            IntentRouter.IntentDefinition intent,
            String nextAction,
            List<String> missingSlots,
        List<String> modelMissingSlots
    ) {
        RenderedResponseTemplate rendered = renderTemplate(intent.id(), intent.name(), nextAction, missingSlots);
        String modelText = stringValue(payload, "assistant_text", "");
        boolean slotsCompatible = "fallback".equals(intent.id()) || sameSlots(modelMissingSlots, missingSlots);
        boolean safeModelText = !modelText.isBlank()
                && slotsCompatible
                && isSafeAssistantText(modelText, intent);
        if ("fallback".equals(intent.id())) {
            return safeModelText
                    ? modelText
                    : polishedTemplateText(message, intent, nextAction, missingSlots, rendered.message());
        }
        if (rendered.preferTemplate()) {
            return polishedTemplateText(message, intent, nextAction, missingSlots, rendered.message());
        }
        if (safeModelText) {
            return modelText;
        }
        return polishedTemplateText(message, intent, nextAction, missingSlots, rendered.message());
    }

    private String templateText(
            String message,
            IntentRouter.IntentDefinition intent,
            String nextAction,
            List<String> missingSlots
    ) {
        RenderedResponseTemplate rendered = renderTemplate(intent.id(), intent.name(), nextAction, missingSlots);
        return polishedTemplateText(message, intent, nextAction, missingSlots, rendered.message());
    }

    private String polishedTemplateText(
            String message,
            IntentRouter.IntentDefinition intent,
            String nextAction,
            List<String> missingSlots,
            String templateText
    ) {
        if (templateText == null || templateText.isBlank()) {
            return "";
        }
        AssistantReplyPolisher.PolishRequest request = new AssistantReplyPolisher.PolishRequest(
                message,
                intent.id(),
                intent.name(),
                intent.taskType(),
                nextAction,
                intent.actionType(),
                normalizeRisk(intent.risk()),
                intent.requiresConfirmation(),
                missingSlots,
                templateText
        );
        return replyPolisher.polish(request)
                .map(String::trim)
                .filter(text -> isSafeAssistantText(text, intent))
                .orElse(templateText);
    }

    private boolean sameSlots(List<String> left, List<String> right) {
        return new LinkedHashSet<>(left).equals(new LinkedHashSet<>(right));
    }

    private boolean isSafeAssistantText(String text, IntentRouter.IntentDefinition intent) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.length() > 600) {
            return false;
        }
        if (trimmed.matches("^安安[，,:：].*")) {
            return false;
        }
        if (TextSupport.containsAny(trimmed, List.of(
                "识别为",
                "next_action",
                "action_draft",
                "backendActionDraft",
                "ActionPlan",
                "JSON",
                "nodeId",
                "fileId",
                "folderId",
                "ownerId",
                "storagePath",
                "objectKey",
                "cosKey",
                "/api/"
        ))) {
            return false;
        }
        return !containsExecutionClaim(trimmed, intent.actionType());
    }

    private boolean containsExecutionClaim(String text, String actionType) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = TextSupport.normalizeText(text);
        List<String> completedClaims = List.of(
                "已删除",
                "已经删除",
                "删除完成",
                "删掉了",
                "删好了",
                "已移入回收站",
                "已经移入回收站",
                "已移动",
                "已经移动",
                "移动完成",
                "已重命名",
                "已经重命名",
                "重命名完成",
                "已改名",
                "已经改名",
                "已分享",
                "已经分享",
                "分享完成",
                "已生成分享链接",
                "已经生成分享链接",
                "已上传",
                "已经上传",
                "上传完成",
                "已创建文件夹",
                "已经创建文件夹",
                "创建完成"
        );
        if (TextSupport.containsAny(normalized, completedClaims)) {
            return true;
        }
        String safeActionType = actionType == null ? "" : actionType;
        return !"none".equals(safeActionType)
                && TextSupport.containsAny(normalized, List.of("已经帮你", "已帮你", "处理好了", "操作完成"));
    }

    private String clarificationQuestion(IntentRouter.IntentDefinition intent, List<String> missingSlots) {
        if ("fallback".equals(intent.id())) {
            return "";
        }
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

    private RenderedResponseTemplate renderTemplate(String intentId, String intentName, String nextAction, List<String> missingSlots) {
        String condition = nextAction == null || nextAction.isBlank() ? "*" : nextAction;
        ResponseTemplate template = responseTemplates.stream()
                .filter(item -> item.matches(intentId, condition))
                .findFirst()
                .orElse(responseTemplates.getLast());
        Map<String, String> values = new LinkedHashMap<>(personaPlaceholders);
        values.put("intent_name", intentName);
        values.put("next_action", nextAction);
        values.put("missing_slots_text", missingSlots.isEmpty() ? "无" : missingSlotLabels(missingSlots));
        return new RenderedResponseTemplate(
                TextSupport.safeFormat(template.messageTemplate(), values),
                template.preferTemplate()
        );
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
                    parseBool(row.getOrDefault("prefer_template", "false")),
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
        values.put("persona_capability_examples_reply", persona.path("capabilityExamplesReply").asText(""));
        values.put("persona_acknowledgement_reply", persona.path("acknowledgementReply").asText(""));
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
            boolean preferTemplate,
            int rowNumber
    ) {
        boolean matches(String actualIntentId, String actualNextAction) {
            return enabled
                    && ("*".equals(intentId) || intentId.equals(actualIntentId))
                    && ("*".equals(nextAction) || nextAction.equals(actualNextAction));
        }
    }

    private record RenderedResponseTemplate(
            String message,
            boolean preferTemplate
    ) {
    }
}
