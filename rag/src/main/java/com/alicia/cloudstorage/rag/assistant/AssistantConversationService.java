package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AssistantConversationService {

    private final IntentRecognitionService intentRecognitionService;
    private final IntentRouter intentRouter;
    private final AssistantConversationStore conversationStore;
    private final CandidateBindingService candidateBindingService;
    private final CandidateSelectionService candidateSelectionService;
    private final ConversationContextResolver conversationContextResolver;
    private final BackendActionDraftService backendActionDraftService;
    private final ActionPlanService actionPlanService;
    private final CollectionPreviewService collectionPreviewService;
    private final List<String> confirmMessages;
    private final List<String> resetMessages;

    public AssistantConversationService(
            IntentRecognitionService intentRecognitionService,
            IntentRouter intentRouter,
            AssistantConversationStore conversationStore,
            CandidateBindingService candidateBindingService,
            CandidateSelectionService candidateSelectionService,
            ConversationContextResolver conversationContextResolver,
            BackendActionDraftService backendActionDraftService,
            ActionPlanService actionPlanService,
            CollectionPreviewService collectionPreviewService,
            RagConfigLoader configLoader
    ) {
        this.intentRecognitionService = intentRecognitionService;
        this.intentRouter = intentRouter;
        this.conversationStore = conversationStore;
        this.candidateBindingService = candidateBindingService;
        this.candidateSelectionService = candidateSelectionService;
        this.conversationContextResolver = conversationContextResolver;
        this.backendActionDraftService = backendActionDraftService;
        this.actionPlanService = actionPlanService;
        this.collectionPreviewService = collectionPreviewService;
        JsonNode controls = configLoader.loadJson("rag/conversation/query_rules.json").path("conversation_controls");
        this.confirmMessages = stringList(controls.path("confirm"));
        this.resetMessages = stringList(controls.path("reset"));
    }

    public IntentRecognitionResponse plan(AssistantPlanRequest request) {
        return plan(request, "");
    }

    public IntentRecognitionResponse plan(AssistantPlanRequest request, String authorizationHeader) {
        String message = request == null || request.message() == null ? "" : request.message().trim();
        AssistantConversationState conversation = isResetMessage(message)
                ? conversationStore.restart()
                : conversationStore.resolve(request == null ? "" : request.conversationId());

        IntentRecognitionResponse baseResponse = intentRecognitionService.recognize(message);
        CandidateSelectionService.SelectionAttempt selectionAttempt = canUseCandidateSelection(conversation)
                ? candidateSelectionService.select(conversation.candidateBinding(), message)
                : CandidateSelectionService.SelectionAttempt.notMatched();
        if (selectionAttempt.matched()) {
            IntentRecognitionResponse selectedResponse = rebuildFromStoredConversation(
                    conversation,
                    baseResponse,
                    "用户选择了上一轮候选。"
            );
            selectedResponse = applyCandidateBindingState(selectedResponse, selectionAttempt.candidateBinding());
            selectedResponse = applyActionPlan(selectedResponse);
            selectedResponse = applyCollectionPreview(selectedResponse, authorizationHeader);
            AssistantConversationState savedConversation = conversationStore.save(conversation, selectedResponse);
            return selectedResponse.withConversation(savedConversation.snapshot(statusFor(selectedResponse)));
        }

        ConversationContextResolver.ContextAttempt contextAttempt = conversationContextResolver.resolve(
                message,
                conversation,
                baseResponse
        );
        ConversationContextResolution contextResolution = contextAttempt.resolution();
        if (contextAttempt.applied() && contextAttempt.response() != null) {
            if (contextResolution == null || !contextResolution.shouldRewrite()) {
                IntentRecognitionResponse answeredResponse = contextAttempt.response();
                AssistantConversationState savedConversation = conversationStore.save(conversation, answeredResponse);
                return answeredResponse.withConversation(savedConversation.snapshot(statusFor(answeredResponse)));
            }
            baseResponse = contextAttempt.response();
        }

        boolean preservePendingIntent = shouldPreservePendingIntent(conversation, message);
        IntentRecognitionResponse response = preservePendingIntent
                ? rebuildFromStoredConversation(conversation, baseResponse, "用户确认或延续上一轮待处理意图。")
                : shouldContinuePendingIntent(conversation, baseResponse, message)
                ? continuePendingIntent(conversation, baseResponse, message)
                : baseResponse;

        CandidateBindingResult contextualBinding = contextualCandidateBinding(conversation, response, contextResolution);
        CandidateBindingResult candidateBinding = contextualBinding != null
                ? contextualBinding
                : preservePendingIntent && shouldReuseCandidateBinding(conversation.candidateBinding())
                ? conversation.candidateBinding()
                : candidateBindingService.bind(response, authorizationHeader);
        response = applyCandidateBindingState(response, candidateBinding);
        response = applyActionPlan(response);
        response = applyCollectionPreview(response, authorizationHeader);
        if (preservePendingIntent && isConfirmMessage(message)) {
            BackendActionDraft backendActionDraft = backendActionDraftService.build(response, candidateBinding, true);
            response = applyBackendActionDraftState(
                    response,
                    backendActionDraft
            );
            response = applyPostBackendActionPlan(response, backendActionDraft, authorizationHeader);
        }
        AssistantConversationState savedConversation = conversationStore.save(conversation, response);
        return response.withConversation(savedConversation.snapshot(statusFor(response)));
    }

    private IntentRecognitionResponse continuePendingIntent(
            AssistantConversationState conversation,
            IntentRecognitionResponse baseResponse,
            String message
    ) {
        Map<String, Object> mergedEntities = new LinkedHashMap<>(conversation.entities());
        mergedEntities.putAll(baseResponse.entities());

        List<String> missingSlots = conversation.pendingSlots();
        if (missingSlots.size() == 1 && !hasValue(mergedEntities.get(missingSlots.getFirst()))) {
            String guessedSlotValue = guessSingleSlotValue(message, missingSlots.getFirst(), baseResponse);
            if (!guessedSlotValue.isBlank()) {
                mergedEntities.put(missingSlots.getFirst(), guessedSlotValue);
            }
        }

        return intentRecognitionService.rebuildForConversation(
                baseResponse,
                conversation.pendingIntentId(),
                mergedEntities,
                "根据上一轮待补充槽位合并当前输入。"
        );
    }

    private IntentRecognitionResponse rebuildFromStoredConversation(
            AssistantConversationState conversation,
            IntentRecognitionResponse baseResponse,
            String reason
    ) {
        return intentRecognitionService.rebuildForConversation(
                baseResponse,
                conversation.pendingIntentId(),
                conversation.entities(),
                reason
        );
    }

    private boolean shouldContinuePendingIntent(
            AssistantConversationState conversation,
            IntentRecognitionResponse baseResponse,
            String message
    ) {
        if (conversation == null
                || !conversation.hasPendingSlots()
                || conversation.pendingIntentId() == null
                || conversation.pendingIntentId().isBlank()
                || isResetMessage(message)
                || isConfirmMessage(message)) {
            return false;
        }

        IntentRouter.IntentRouteResult localRoute = intentRouter.route(message);
        if (!"fallback".equals(localRoute.intent()) && !conversation.pendingIntentId().equals(localRoute.intent())) {
            return false;
        }

        return "fallback".equals(baseResponse.intentId())
                || conversation.pendingIntentId().equals(baseResponse.intentId())
                || !baseResponse.entities().isEmpty()
                || !localRoute.entities().isEmpty()
                || localRoute.intent().equals("fallback");
    }

    private boolean shouldPreservePendingIntent(AssistantConversationState conversation, String message) {
        return isConfirmMessage(message)
                && conversation != null
                && conversation.pendingIntentId() != null
                && !conversation.pendingIntentId().isBlank()
                && conversation.pendingActionDraft() != null
                && !"search".equals(conversation.pendingActionDraft().type());
    }

    private IntentRecognitionResponse applyCandidateBindingState(
            IntentRecognitionResponse response,
            CandidateBindingResult candidateBinding
    ) {
        IntentRecognitionResponse boundResponse = response.withCandidateBinding(candidateBinding);
        if (candidateBinding == null) {
            return boundResponse;
        }
        return switch (candidateBinding.status()) {
            case "search_results_ready" -> intentRecognitionService.withFlowState(
                    boundResponse,
                    "show_search_results",
                    candidateBinding.message()
            );
            case "multiple_candidates", "candidate_selection_out_of_range" -> intentRecognitionService.withFlowState(
                    boundResponse,
                    "wait_for_candidate_selection",
                    candidateBinding.message()
            );
            case "single_candidate", "selected_candidate" -> intentRecognitionService.withFlowState(
                    boundResponse,
                    "wait_for_user_confirmation",
                    candidateBinding.message()
            );
            default -> boundResponse;
        };
    }

    private IntentRecognitionResponse applyBackendActionDraftState(
            IntentRecognitionResponse response,
            BackendActionDraft backendActionDraft
    ) {
        IntentRecognitionResponse draftedResponse = response.withBackendActionDraft(backendActionDraft);
        if (backendActionDraft == null || backendActionDraft.nextAction() == null || backendActionDraft.nextAction().isBlank()) {
            return draftedResponse;
        }
        return switch (backendActionDraft.status()) {
            case "backend_action_ready", "client_action_required" -> intentRecognitionService.withFlowState(
                    draftedResponse,
                    backendActionDraft.nextAction(),
                    backendActionDraft.message()
            );
            default -> draftedResponse;
        };
    }

    private IntentRecognitionResponse applyActionPlan(IntentRecognitionResponse response) {
        return response == null ? null : response.withActionPlan(actionPlanService.build(response));
    }

    private IntentRecognitionResponse applyCollectionPreview(
            IntentRecognitionResponse response,
            String authorizationHeader
    ) {
        return collectionPreviewService.apply(response, authorizationHeader);
    }

    private IntentRecognitionResponse applyPostBackendActionPlan(
            IntentRecognitionResponse response,
            BackendActionDraft backendActionDraft,
            String authorizationHeader
    ) {
        if (response.actionPlan() != null && "collection".equals(response.actionPlan().planKind())) {
            return response.withActionPlan(actionPlanService.withBackendActionDraft(response.actionPlan(), backendActionDraft));
        }
        response = applyActionPlan(response);
        return applyCollectionPreview(response, authorizationHeader);
    }

    private boolean canUseCandidateSelection(AssistantConversationState conversation) {
        return conversation != null
                && conversation.pendingIntentId() != null
                && !conversation.pendingIntentId().isBlank()
                && conversation.pendingActionDraft() != null
                && !"search".equals(conversation.pendingActionDraft().type())
                && conversation.candidateBinding() != null
                && !conversation.candidateBinding().candidates().isEmpty();
    }

    private boolean shouldReuseCandidateBinding(CandidateBindingResult candidateBinding) {
        if (candidateBinding == null || candidateBinding.candidates().isEmpty()) {
            return false;
        }
        return switch (candidateBinding.status()) {
            case "multiple_candidates", "single_candidate", "selected_candidate", "candidate_selection_out_of_range" -> true;
            default -> false;
        };
    }

    private CandidateBindingResult contextualCandidateBinding(
            AssistantConversationState conversation,
            IntentRecognitionResponse response,
            ConversationContextResolution contextResolution
    ) {
        if (conversation == null
                || conversation.focus() == null
                || contextResolution == null
                || !List.of("previous_candidate", "selected_candidate").contains(contextResolution.referent())
                || response == null
                || response.actionDraft() == null
                || !response.actionDraft().needsBackendBinding()) {
            return null;
        }

        String actionType = response.actionDraft().type();
        if (!List.of("delete", "share", "rename").contains(actionType)) {
            return null;
        }

        if ("selected_candidate".equals(contextResolution.referent()) && contextResolution.selectedIndex() != null) {
            return conversation.focus().selectedBinding(
                    contextResolution.selectedIndex(),
                    "已根据上一轮候选选择锁定目标，等待用户确认。"
            );
        }

        return conversation.focus().selectedBinding("已根据上一轮上下文锁定候选，等待用户确认。");
    }

    private String guessSingleSlotValue(
            String message,
            String slotId,
            IntentRecognitionResponse baseResponse
    ) {
        Object value = baseResponse.entities().get(slotId);
        if (hasValue(value)) {
            return String.valueOf(value).trim();
        }
        if ("operation".equals(slotId) || message == null || message.isBlank()) {
            return "";
        }
        return TextSupport.sanitizeNodeName(message);
    }

    private boolean hasValue(Object value) {
        return value != null && !String.valueOf(value).trim().isBlank();
    }

    private boolean isResetMessage(String message) {
        return resetMessages.contains(message == null ? "" : message.trim());
    }

    private boolean isConfirmMessage(String message) {
        return confirmMessages.contains(message == null ? "" : message.trim());
    }

    private String statusFor(IntentRecognitionResponse response) {
        if (!response.missingSlots().isEmpty() || "ask_clarification".equals(response.nextAction())) {
            return "waiting_for_clarification";
        }
        if (response.backendActionDraft() != null && "backend_action_ready".equals(response.backendActionDraft().status())) {
            return "backend_action_ready";
        }
        if (response.backendActionDraft() != null && "client_action_required".equals(response.backendActionDraft().status())) {
            return "client_action_required";
        }
        if (response.actionPlan() != null && "collection".equals(response.actionPlan().planKind())) {
            if ("collection_review_required".equals(response.actionPlan().status())) {
                return "collection_review_required";
            }
            if ("binding_required".equals(response.actionPlan().status())) {
                return "waiting_for_collection_preview";
            }
        }
        if (response.candidateBinding() != null && "multiple_candidates".equals(response.candidateBinding().status())) {
            return "waiting_for_candidate_selection";
        }
        if (response.candidateBinding() != null && "search_results_ready".equals(response.candidateBinding().status())) {
            return "search_results_ready";
        }
        if (response.candidateBinding() != null && "candidate_selection_out_of_range".equals(response.candidateBinding().status())) {
            return "waiting_for_candidate_selection";
        }
        if (response.candidateBinding() != null && "single_candidate".equals(response.candidateBinding().status())) {
            return "waiting_for_user_confirmation";
        }
        if (response.candidateBinding() != null && "selected_candidate".equals(response.candidateBinding().status())) {
            return "waiting_for_user_confirmation";
        }
        if ("wait_for_candidate_selection".equals(response.nextAction())) {
            return "waiting_for_candidate_selection";
        }
        if ("show_search_results".equals(response.nextAction())) {
            return "search_results_ready";
        }
        if ("wait_for_user_confirmation".equals(response.nextAction())) {
            return "waiting_for_user_confirmation";
        }
        if ("handoff_to_backend".equals(response.nextAction())) {
            return "backend_action_ready";
        }
        if ("handoff_to_client_upload".equals(response.nextAction())) {
            return "client_action_required";
        }
        if ("respond_only".equals(response.nextAction())) {
            return "responded";
        }
        if ("wait_for_backend_binding".equals(response.nextAction())) {
            return "waiting_for_backend_binding";
        }
        return "idle";
    }

    private List<String> stringList(JsonNode node) {
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
