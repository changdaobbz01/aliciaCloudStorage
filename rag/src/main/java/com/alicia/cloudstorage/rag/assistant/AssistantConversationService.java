package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.alicia.cloudstorage.rag.assistant.AssistantSemanticOrchestrator.Kind;
import static com.alicia.cloudstorage.rag.assistant.RecognitionTrace.Outcome;
import static com.alicia.cloudstorage.rag.assistant.RecognitionTrace.Stage;

@Service
public class AssistantConversationService {

    private final IntentRecognitionService intentRecognitionService;
    private final AssistantSemanticOrchestrator semanticOrchestrator;
    private final AssistantConversationStore conversationStore;
    private final CandidateBindingService candidateBindingService;
    private final BackendActionDraftService backendActionDraftService;
    private final ActionPlanService actionPlanService;
    private final CollectionPreviewService collectionPreviewService;
    private final CollectionOperationSelectorResolver collectionOperationSelectorResolver;
    private final ScopedCollectionPlanningService scopedCollectionPlanningService;
    private final AssistantInteractionService interactionService = new AssistantInteractionService();
    private final List<String> resetMessages;

    public AssistantConversationService(
            IntentRecognitionService intentRecognitionService,
            AssistantSemanticOrchestrator semanticOrchestrator,
            AssistantConversationStore conversationStore,
            CandidateBindingService candidateBindingService,
            BackendActionDraftService backendActionDraftService,
            ActionPlanService actionPlanService,
            CollectionPreviewService collectionPreviewService,
            CollectionOperationSelectorResolver collectionOperationSelectorResolver,
            ScopedCollectionPlanningService scopedCollectionPlanningService,
            RagConfigLoader configLoader
    ) {
        this.intentRecognitionService = intentRecognitionService;
        this.semanticOrchestrator = semanticOrchestrator;
        this.conversationStore = conversationStore;
        this.candidateBindingService = candidateBindingService;
        this.backendActionDraftService = backendActionDraftService;
        this.actionPlanService = actionPlanService;
        this.collectionPreviewService = collectionPreviewService;
        this.collectionOperationSelectorResolver = collectionOperationSelectorResolver;
        this.scopedCollectionPlanningService = scopedCollectionPlanningService;
        JsonNode controls = configLoader.loadJson("rag/conversation/query_rules.json").path("conversation_controls");
        this.resetMessages = stringList(controls.path("reset"));
    }

    public IntentRecognitionResponse plan(AssistantPlanRequest request) {
        return plan(request, "");
    }

    public IntentRecognitionResponse plan(AssistantPlanRequest request, String authorizationHeader) {
        return plan(request, authorizationHeader, null);
    }

    IntentRecognitionResponse plan(AssistantPlanRequest request, String authorizationHeader, Long owner) {
        String message = request == null || request.message() == null ? "" : request.message().trim();
        AssistantClientContext clientContext = request == null
                ? AssistantClientContext.empty()
                : request.clientContext();
        AssistantClientEvent clientEvent = request == null ? AssistantClientEvent.none() : request.clientEvent();
        long stageStarted = RecognitionTrace.tick();
        if (clientEvent.isExecutionTerminal()) {
            conversationStore.complete(request == null ? "" : request.conversationId(), authorizationHeader, owner);
            scopedCollectionPlanningService.complete(clientEvent.planId(), authorizationHeader);
            String acknowledgement = switch (clientEvent.type()) {
                case "ACTION_COMPLETED" -> "操作结果已同步，我会按最新文件状态继续为你处理。";
                case "ACTION_CANCELLED" -> "这次操作已经取消，没有继续提交文件变更。";
                default -> "操作没有成功完成，我已结束旧计划。你可以调整要求后重新发起。";
            };
            IntentRecognitionResponse response = intentRecognitionService
                    .recognizeLocal(acknowledgement, "客户端执行结果同步")
                    .withAssistantText(acknowledgement);
            AssistantConversationState newConversation = conversationStore.restart(authorizationHeader);
            RecognitionTrace.session(Outcome.TERMINAL_EVENT, stageStarted, newConversation);
            return response.withConversation(newConversation.snapshot("completed"));
        }
        boolean reset = isResetMessage(message);
        String requestedConversation = request == null ? "" : request.conversationId();
        AssistantConversationState conversation = reset
                ? conversationStore.reset(requestedConversation, authorizationHeader, owner)
                : conversationStore.resolve(requestedConversation, authorizationHeader, owner);
        if (reset) {
            RecognitionTrace.session(Outcome.RESET, stageStarted, conversation);
        }

        var decision = semanticOrchestrator.decide(message, conversation, clientContext, clientEvent, reset);
        IntentRecognitionResponse response = decision.response();
        if (decision.kind() == Kind.CONTEXT_REPLY) {
            return saveTurn(conversation, response, message, owner);
        }
        if (decision.kind() == Kind.CANDIDATE_SELECTION) {
            response = planSelection(conversation, decision, clientEvent, authorizationHeader, clientContext, message);
            return saveTurn(conversation, response, message, owner);
        }
        boolean confirmed = decision.kind() == Kind.CONFIRMATION;
        boolean filledSlot = decision.kind() == Kind.SLOT_FILL;

        if (collectionOperationSelectorResolver.isScopedCollection(response)) {
            response = planScopedCollection(
                    response,
                    conversation,
                    authorizationHeader,
                    clientContext
            );
            if (confirmed) {
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
            return saveTurn(conversation, response, message, owner);
        }

        stageStarted = RecognitionTrace.tick();
        CandidateBindingResult candidateBinding;
        boolean blocked = decision.kind() == Kind.BLOCKED || AssistantFlowPolicy.blocksExecution(response);
        if (blocked) {
            candidateBinding = response.candidateBinding() == null
                    ? CandidateBindingResult.skipped(
                    "waiting_for_clarification",
                    "当前语义仍需澄清，暂不查询真实候选。"
            )
                    : response.candidateBinding();
        } else {
            CandidateBindingResult contextualBinding = contextualCandidateBinding(
                    conversation,
                    response,
                    decision.contextResolution()
            );
            if (contextualBinding == null) {
                contextualBinding = pendingTargetBinding(conversation, response, filledSlot);
            }
            AssistantClientContext bindingClientContext = contextualClientContext(
                    conversation,
                    response,
                    clientContext
            );
            candidateBinding = contextualBinding != null
                    ? contextualBinding
                    : (confirmed || filledSlot)
                    && shouldReuseCandidateBinding(conversation.candidateBinding())
                    ? conversation.candidateBinding()
                    : candidateBindingService.bind(
                            response,
                            authorizationHeader,
                            bindingClientContext
                    );
        }
        response = applyCandidateBindingState(response, candidateBinding);
        RecognitionTrace.decision(Stage.CANDIDATE_BINDING, blocked ? Outcome.SKIPPED : Outcome.OBSERVED, stageStarted, response);
        if (confirmed && hasReviewedCollectionPlan(conversation)) {
            response = response.withActionPlan(conversation.pendingActionPlan());
            RecognitionTrace.decision(Stage.ACTION_PLAN, Outcome.RESTORED, RecognitionTrace.tick(), response);
        } else {
            response = applyActionPlan(response, clientContext);
            response = applyCollectionPreview(response, authorizationHeader);
        }
        if (confirmed) {
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
        if (decision.polishReply()) {
            response = intentRecognitionService.polishGeneratedReply(message, response);
        }
        return saveTurn(conversation, response, message, owner);
    }

    private IntentRecognitionResponse planSelection(
            AssistantConversationState conversation,
            AssistantSemanticOrchestrator.Decision decision,
            AssistantClientEvent clientEvent,
            String authorizationHeader,
            AssistantClientContext clientContext,
            String message
    ) {
        IntentRecognitionResponse selected = decision.response();
        if (collectionOperationSelectorResolver.isScopedCollection(selected)) {
            if (!scopedCollectionPlanningService.acceptsSelection(conversation, clientEvent)) {
                return planScopedCollection(selected, conversation, authorizationHeader, clientContext)
                        .withAssistantText("这个选择不属于当前计划，请根据当前候选列表重新选择。");
            }
            selected = scopedCollectionPlanningService.applySelection(selected, conversation, decision.selectedBinding());
            selected = planScopedCollection(selected, conversation, authorizationHeader, clientContext);
            return intentRecognitionService.polishGeneratedReply(message, selected);
        }
        long started = RecognitionTrace.tick();
        selected = applyCandidateBindingState(selected, decision.selectedBinding());
        RecognitionTrace.decision(Stage.CANDIDATE_BINDING, Outcome.SELECTED, started, selected);
        return applyCollectionPreview(applyActionPlan(selected, clientContext), authorizationHeader);
    }

    private IntentRecognitionResponse saveTurn(
            AssistantConversationState conversation, IntentRecognitionResponse response, String message, Long owner
    ) {
        IntentRecognitionResponse interactive = response.withInteraction(interactionService.build(response));
        AssistantConversationState saved = conversationStore.save(conversation, interactive, message, owner);
        return interactive.withConversation(saved.snapshot(statusFor(interactive)));
    }

    private IntentRecognitionResponse applyCandidateBindingState(
            IntentRecognitionResponse response,
            CandidateBindingResult candidateBinding
    ) {
        IntentRecognitionResponse boundResponse = response.withCandidateBinding(candidateBinding);
        if (candidateBinding == null) {
            return boundResponse;
        }
        if (AssistantFlowPolicy.requiresClarification(response)) {
            return boundResponse;
        }
        return switch (candidateBinding.status()) {
            case "missing_folder_scope", "ambiguous_folder_scope" -> intentRecognitionService.withFlowState(
                    boundResponse,
                    "ask_clarification",
                    candidateBinding.message()
            );
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

    private IntentRecognitionResponse planScopedCollection(
            IntentRecognitionResponse response,
            AssistantConversationState conversation,
            String authorizationHeader,
            AssistantClientContext clientContext
    ) {
        long started = RecognitionTrace.tick();
        IntentRecognitionResponse planned = scopedCollectionPlanningService.plan(response, conversation, authorizationHeader, clientContext);
        RecognitionTrace.decision(Stage.ACTION_PLAN, Outcome.OBSERVED, started, planned);
        return planned;
    }

    private IntentRecognitionResponse applyActionPlan(
            IntentRecognitionResponse response,
            AssistantClientContext clientContext
    ) {
        long started = RecognitionTrace.tick();
        IntentRecognitionResponse planned = response == null ? null : response.withActionPlan(actionPlanService.build(response, clientContext));
        RecognitionTrace.decision(Stage.ACTION_PLAN, Outcome.OBSERVED, started, planned);
        return planned;
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
                || !List.of("SEARCH", "LIST_CHILDREN").contains(response.semanticFrame().operation())
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

    private CandidateBindingResult pendingTargetBinding(
            AssistantConversationState conversation,
            IntentRecognitionResponse response,
            boolean continuePendingIntent
    ) {
        if (!continuePendingIntent
                || conversation == null
                || conversation.pendingSlots().contains("target_name")
                || conversation.focus() == null
                || !conversation.focus().hasCandidateContext()
                || response == null
                || response.actionDraft() == null
                || !List.of("delete", "move", "rename", "share").contains(response.actionDraft().type())) {
            return null;
        }
        CandidateItem candidate = candidateForReference(
                conversation.focus(),
                conversation.semanticFrame().reference()
        );
        Object targetName = response.entities().get("target_name");
        if (candidate == null || targetName == null || !candidate.name().equals(String.valueOf(targetName))) {
            return null;
        }
        int candidateIndex = conversation.focus().candidateBinding().candidates().indexOf(candidate);
        if (candidateIndex < 0) {
            return null;
        }
        return conversation.focus().selectedBinding(
                candidateIndex + 1,
                "已保留上一轮锁定的真实目标，等待继续确认。"
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

    private boolean isResetMessage(String message) {
        return resetMessages.contains(message == null ? "" : message.trim());
    }

    private String statusFor(IntentRecognitionResponse response) {
        if (AssistantFlowPolicy.requiresClarification(response)) {
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
