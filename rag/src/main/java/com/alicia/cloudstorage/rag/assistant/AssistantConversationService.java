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
    private final CollectionOperationSelectorResolver collectionOperationSelectorResolver;
    private final NavigationOperationResolver navigationOperationResolver;
    private final ScopedCollectionPlanningService scopedCollectionPlanningService;
    private final ExecutableConstraintGuard executableConstraintGuard;
    private final AssistantInteractionService interactionService = new AssistantInteractionService();
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
            CollectionOperationSelectorResolver collectionOperationSelectorResolver,
            NavigationOperationResolver navigationOperationResolver,
            ScopedCollectionPlanningService scopedCollectionPlanningService,
            ExecutableConstraintGuard executableConstraintGuard,
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
        this.collectionOperationSelectorResolver = collectionOperationSelectorResolver;
        this.navigationOperationResolver = navigationOperationResolver;
        this.scopedCollectionPlanningService = scopedCollectionPlanningService;
        this.executableConstraintGuard = executableConstraintGuard;
        JsonNode controls = configLoader.loadJson("rag/conversation/query_rules.json").path("conversation_controls");
        this.confirmMessages = stringList(controls.path("confirm"));
        this.resetMessages = stringList(controls.path("reset"));
    }

    public IntentRecognitionResponse plan(AssistantPlanRequest request) {
        return plan(request, "");
    }

    public IntentRecognitionResponse plan(AssistantPlanRequest request, String authorizationHeader) {
        String message = request == null || request.message() == null ? "" : request.message().trim();
        AssistantClientContext clientContext = request == null
                ? AssistantClientContext.empty()
                : request.clientContext();
        AssistantClientEvent clientEvent = request == null ? AssistantClientEvent.none() : request.clientEvent();
        if (clientEvent.isExecutionTerminal()) {
            conversationStore.complete(request == null ? "" : request.conversationId(), authorizationHeader);
            scopedCollectionPlanningService.complete(clientEvent.planId(), authorizationHeader);
            String acknowledgement = switch (clientEvent.type()) {
                case "ACTION_COMPLETED" -> "操作结果已同步，我会按最新文件状态继续为你处理。";
                case "ACTION_CANCELLED" -> "这次操作已经取消，没有继续提交文件变更。";
                default -> "操作没有成功完成，我已结束旧计划。你可以调整要求后重新发起。";
            };
            IntentRecognitionResponse response = intentRecognitionService
                    .recognizeLocal(acknowledgement, "客户端执行结果同步")
                    .withAssistantText(acknowledgement);
            return response.withConversation(conversationStore.restart(authorizationHeader).snapshot("completed"));
        }
        AssistantConversationState conversation = isResetMessage(message)
                ? conversationStore.restart(authorizationHeader)
                : conversationStore.resolve(request == null ? "" : request.conversationId(), authorizationHeader);

        boolean deterministicCollection = collectionOperationSelectorResolver.handles(message, conversation);
        boolean deterministicNavigation = navigationOperationResolver.handles(message, conversation);
        boolean deterministicSafetyBoundary = executableConstraintGuard.handlesUnsupportedConstraint(message);
        IntentRecognitionResponse baseResponse = deterministicCollection || deterministicNavigation || deterministicSafetyBoundary
                ? intentRecognitionService.recognizeLocal(message, "高置信目录集合语义由服务端结构化解析")
                : intentRecognitionService.recognize(message, conversation, clientContext);
        baseResponse = collectionOperationSelectorResolver.apply(message, conversation, baseResponse);
        baseResponse = navigationOperationResolver.apply(message, conversation, baseResponse);
        baseResponse = executableConstraintGuard.apply(message, baseResponse);
        if (collectionOperationSelectorResolver.isScopedCollection(baseResponse)
                && !clientContext.supportsAction(baseResponse.actionDraft().type())) {
            baseResponse = baseResponse.withCapabilityBoundary(
                    "client_action_contract_outdated",
                    "当前移动端版本还不能安全执行这种目录批量操作，请更新应用后再试。",
                    "更新移动端后，可使用目录内全部文件的批量移动和删除。"
            );
        }
        CandidateSelectionService.SelectionAttempt selectionAttempt = canUseCandidateSelection(conversation)
                ? candidateSelectionService.select(
                        conversation.candidateBinding(),
                        message,
                        request == null ? AssistantClientEvent.none() : request.clientEvent()
                )
                : CandidateSelectionService.SelectionAttempt.notMatched();
        if (selectionAttempt.matched()) {
            IntentRecognitionResponse selectedResponse = rebuildFromStoredConversation(
                    conversation,
                    baseResponse,
                    "用户选择了上一轮候选。"
            );
            CandidateBindingResult selectedBinding = selectionAttempt.candidateBinding();
            if (conversation.pendingActionDraft() != null
                    && List.of("collection.move", "collection.trash").contains(conversation.pendingActionDraft().type())) {
                selectedResponse = collectionOperationSelectorResolver.restoreStored(selectedResponse, conversation);
                if (!scopedCollectionPlanningService.acceptsSelection(conversation, clientEvent)) {
                    selectedResponse = scopedCollectionPlanningService.plan(
                            selectedResponse,
                            conversation,
                            authorizationHeader,
                            clientContext
                    ).withAssistantText("这个选择不属于当前计划，请根据当前候选列表重新选择。");
                    selectedResponse = selectedResponse.withInteraction(interactionService.build(selectedResponse));
                    AssistantConversationState savedConversation = conversationStore.save(conversation, selectedResponse);
                    return selectedResponse.withConversation(savedConversation.snapshot(statusFor(selectedResponse)));
                }
                selectedResponse = scopedCollectionPlanningService.applySelection(
                        selectedResponse,
                        conversation,
                        selectedBinding
                );
                selectedResponse = scopedCollectionPlanningService.plan(
                        selectedResponse,
                        conversation,
                        authorizationHeader,
                        clientContext
                );
                selectedResponse = intentRecognitionService.polishGeneratedReply(message, selectedResponse);
                selectedResponse = selectedResponse.withInteraction(interactionService.build(selectedResponse));
                AssistantConversationState savedConversation = conversationStore.save(conversation, selectedResponse);
                return selectedResponse.withConversation(savedConversation.snapshot(statusFor(selectedResponse)));
            }
            CandidateItem selectedCandidate = selectedBinding == null ? null : selectedBinding.selectedCandidate();
            Integer selectedIndex = selectedCandidateIndex(selectedBinding, selectedCandidate);
            SemanticFrame selectedFrame = conversation.semanticFrame()
                    .forCandidateSelection(selectedCandidate, selectedIndex);
            selectedResponse = selectedResponse.withSemanticFrame(
                    selectedFrame,
                    selectedResponse.entities(),
                    selectedResponse.actionDraft()
            );
            selectedResponse = applyCandidateBindingState(selectedResponse, selectedBinding);
            selectedResponse = applyActionPlan(selectedResponse, clientContext);
            selectedResponse = applyCollectionPreview(selectedResponse, authorizationHeader);
            selectedResponse = selectedResponse.withInteraction(interactionService.build(selectedResponse));
            AssistantConversationState savedConversation = conversationStore.save(conversation, selectedResponse);
            return selectedResponse.withConversation(savedConversation.snapshot(statusFor(selectedResponse)));
        }

        ConversationContextResolver.ContextAttempt contextAttempt = !collectionOperationSelectorResolver.isScopedCollection(baseResponse)
                && "FOLLOW_UP".equals(baseResponse.semanticFrame().relation())
                && !"SEARCH".equals(baseResponse.semanticFrame().operation())
                ? conversationContextResolver.resolve(
                        message,
                        conversation,
                        baseResponse,
                        baseResponse.semanticFrame()
                )
                : ConversationContextResolver.ContextAttempt.notApplied();
        ConversationContextResolution contextResolution = contextAttempt.resolution();
        if (contextAttempt.applied() && contextAttempt.response() != null) {
            if (contextResolution == null || !contextResolution.shouldRewrite()) {
                IntentRecognitionResponse answeredResponse = contextAttempt.response();
                answeredResponse = answeredResponse.withInteraction(interactionService.build(answeredResponse));
                AssistantConversationState savedConversation = conversationStore.save(conversation, answeredResponse);
                return answeredResponse.withConversation(savedConversation.snapshot(statusFor(answeredResponse)));
            }
            baseResponse = contextAttempt.response();
        }

        boolean preservePendingIntent = shouldPreservePendingIntent(conversation, message);
        boolean continuePendingIntent = shouldContinuePendingIntent(conversation, baseResponse, message);
        IntentRecognitionResponse response = preservePendingIntent
                ? rebuildFromStoredConversation(conversation, baseResponse, "用户确认或延续上一轮待处理意图。")
                : continuePendingIntent
                ? continuePendingIntent(conversation, baseResponse, message)
                : baseResponse;
        if (preservePendingIntent || continuePendingIntent) {
            response = collectionOperationSelectorResolver.restoreStored(response, conversation);
        }

        if (collectionOperationSelectorResolver.isScopedCollection(response)) {
            response = scopedCollectionPlanningService.plan(
                    response,
                    conversation,
                    authorizationHeader,
                    clientContext
            );
            if (preservePendingIntent && isConfirmMessage(message)) {
                BackendActionDraft backendActionDraft = backendActionDraftService.build(
                        response,
                        response.candidateBinding(),
                        true,
                        clientContext,
                        authorizationHeader
                );
                response = applyBackendActionDraftState(response, backendActionDraft);
                response = applyPostBackendActionPlan(
                        response,
                        backendActionDraft,
                        authorizationHeader,
                        clientContext
                );
            }
            response = intentRecognitionService.polishGeneratedReply(message, response);
            response = response.withInteraction(interactionService.build(response));
            AssistantConversationState savedConversation = conversationStore.save(conversation, response);
            return response.withConversation(savedConversation.snapshot(statusFor(response)));
        }

        CandidateBindingResult contextualBinding = contextualCandidateBinding(conversation, response, contextResolution);
        AssistantClientContext bindingClientContext = contextualClientContext(conversation, response, clientContext);
        CandidateBindingResult candidateBinding = contextualBinding != null
                ? contextualBinding
                : (preservePendingIntent || continuePendingIntent)
                && shouldReuseCandidateBinding(conversation.candidateBinding())
                ? conversation.candidateBinding()
                : candidateBindingService.bind(
                        response,
                        authorizationHeader,
                        bindingClientContext
                );
        response = applyCandidateBindingState(response, candidateBinding);
        if (preservePendingIntent && hasReviewedCollectionPlan(conversation)) {
            response = response.withActionPlan(conversation.pendingActionPlan());
        } else {
            response = applyActionPlan(response, clientContext);
            response = applyCollectionPreview(response, authorizationHeader);
        }
        if (preservePendingIntent && isConfirmMessage(message)) {
            BackendActionDraft backendActionDraft = backendActionDraftService.build(
                    response,
                    candidateBinding,
                    true,
                    clientContext,
                    authorizationHeader
            );
            response = applyBackendActionDraftState(
                    response,
                    backendActionDraft
            );
            response = applyPostBackendActionPlan(
                    response,
                    backendActionDraft,
                    authorizationHeader,
                    clientContext
            );
        }
        if (deterministicCollection || deterministicNavigation || deterministicSafetyBoundary || isGeneratedSafetyReply(response)) {
            response = intentRecognitionService.polishGeneratedReply(message, response);
        }
        response = response.withInteraction(interactionService.build(response));
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
                || isCapabilityBoundary(baseResponse)
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

    private boolean isCapabilityBoundary(IntentRecognitionResponse response) {
        return response != null
                && response.fallbackReason() != null
                && response.fallbackReason().startsWith("capability_boundary:");
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
        if (!response.missingSlots().isEmpty() || "ask_clarification".equals(response.nextAction())) {
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

    private IntentRecognitionResponse applyActionPlan(
            IntentRecognitionResponse response,
            AssistantClientContext clientContext
    ) {
        return response == null ? null : response.withActionPlan(actionPlanService.build(response, clientContext));
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
            String authorizationHeader,
            AssistantClientContext clientContext
    ) {
        if (response.actionPlan() != null && "collection".equals(response.actionPlan().planKind())) {
            return response.withActionPlan(actionPlanService.withBackendActionDraft(response.actionPlan(), backendActionDraft));
        }
        response = applyActionPlan(response, clientContext);
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

    private Integer selectedCandidateIndex(CandidateBindingResult binding, CandidateItem selectedCandidate) {
        if (binding == null || selectedCandidate == null) {
            return null;
        }
        for (int index = 0; index < binding.candidates().size(); index++) {
            CandidateItem candidate = binding.candidates().get(index);
            if (candidate.equals(selectedCandidate)) {
                return index + 1;
            }
        }
        return null;
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

    private boolean hasReviewedCollectionPlan(AssistantConversationState conversation) {
        return conversation != null
                && conversation.pendingActionPlan() != null
                && "collection".equals(conversation.pendingActionPlan().planKind())
                && "collection_review_required".equals(conversation.pendingActionPlan().status());
    }

    private CandidateBindingResult contextualCandidateBinding(
            AssistantConversationState conversation,
            IntentRecognitionResponse response,
            ConversationContextResolution contextResolution
    ) {
        if (conversation == null
                || conversation.focus() == null
                || response == null
                || response.actionDraft() == null
                || !response.actionDraft().needsBackendBinding()) {
            return null;
        }

        SemanticFrame frame = response.semanticFrame();
        if (frame != null
                && List.of("NAVIGATE", "OPEN_FILE").contains(frame.operation())
                && "PREVIOUS_RESULTS".equals(frame.scope().type())) {
            return conversation.focus().selectedBinding("已根据上一轮上下文锁定要打开的候选。");
        }
        if (contextResolution == null
                || !List.of("previous_candidate", "selected_candidate").contains(contextResolution.referent())) {
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

    private AssistantClientContext contextualClientContext(
            AssistantConversationState conversation,
            IntentRecognitionResponse response,
            AssistantClientContext clientContext
    ) {
        AssistantClientContext safeContext = clientContext == null ? AssistantClientContext.empty() : clientContext;
        if (conversation == null
                || response == null
                || response.semanticFrame() == null
                || !"SEARCH".equals(response.semanticFrame().operation())
                || !"LIST_CHILDREN".equals(response.semanticFrame().query().mode())
                || !"PREVIOUS_RESULTS".equals(response.semanticFrame().scope().type())
                || conversation.focus() == null) {
            return safeContext;
        }
        CandidateItem folder = candidateForReference(conversation.focus(), response.semanticFrame().reference());
        if (folder == null || folder.nodeId() == null || !"FOLDER".equalsIgnoreCase(folder.type())) {
            return safeContext;
        }
        return new AssistantClientContext(
                folder.nodeId(),
                folder.path() == null || folder.path().isBlank() ? folder.name() : folder.path(),
                safeContext.availableClientInputs(),
                safeContext.actionContractVersion(),
                safeContext.supportedActionTypes()
        );
    }

    private CandidateItem candidateForReference(
            AssistantConversationFocus focus,
            SemanticFrame.Reference reference
    ) {
        if (focus == null) {
            return null;
        }
        if (reference != null && reference.candidateIndex() != null && focus.candidateBinding() != null) {
            int index = reference.candidateIndex() - 1;
            if (index >= 0 && index < focus.candidateBinding().candidates().size()) {
                return focus.candidateBinding().candidates().get(index);
            }
        }
        if (reference != null && reference.candidateId() != null && focus.candidateBinding() != null) {
            return focus.candidateBinding().candidates().stream()
                    .filter(candidate -> reference.candidateId().equals(candidate.nodeId()))
                    .findFirst()
                    .orElse(focus.effectiveCandidate());
        }
        return focus.effectiveCandidate();
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

    private boolean isGeneratedSafetyReply(IntentRecognitionResponse response) {
        return response != null
                && response.reason() != null
                && response.reason().startsWith("capability_boundary:");
    }

    private boolean isConfirmMessage(String message) {
        String normalized = normalizeControlMessage(message);
        return confirmMessages.stream()
                .map(this::normalizeControlMessage)
                .anyMatch(normalized::equals);
    }

    private String normalizeControlMessage(String message) {
        return message == null
                ? ""
                : message.trim().replaceAll("[\\s，。,.!?！？]+", "");
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
