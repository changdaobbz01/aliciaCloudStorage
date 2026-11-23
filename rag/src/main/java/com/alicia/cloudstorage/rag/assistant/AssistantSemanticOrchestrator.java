package com.alicia.cloudstorage.rag.assistant;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.alicia.cloudstorage.rag.assistant.RecognitionTrace.Outcome;
import static com.alicia.cloudstorage.rag.assistant.RecognitionTrace.Stage;

@Service
final class AssistantSemanticOrchestrator {
    private final IntentRecognitionService recognition;
    private final IntentRouter intentRouter;
    private final CandidateSelectionService candidateSelection;
    private final ConversationContextResolver contextResolver;
    private final CollectionOperationSelectorResolver collections;
    private final NavigationOperationResolver navigation;
    private final ExecutableConstraintGuard constraints;
    private final PendingSlotResolutionService pendingSlots = new PendingSlotResolutionService();
    private final Set<String> confirmMessages;

    AssistantSemanticOrchestrator(
            IntentRecognitionService recognition,
            IntentRouter intentRouter,
            CandidateSelectionService candidateSelection,
            ConversationContextResolver contextResolver,
            CollectionOperationSelectorResolver collections,
            NavigationOperationResolver navigation,
            ExecutableConstraintGuard constraints,
            RagConfigLoader configLoader
    ) {
        this.recognition = recognition;
        this.intentRouter = intentRouter;
        this.candidateSelection = candidateSelection;
        this.contextResolver = contextResolver;
        this.collections = collections;
        this.navigation = navigation;
        this.constraints = constraints;
        Set<String> messages = new HashSet<>();
        configLoader.loadJson("rag/conversation/query_rules.json").path("conversation_controls").path("confirm")
                .forEach(node -> messages.add(normalizeControl(node.asText(""))));
        messages.remove("");
        confirmMessages = Set.copyOf(messages);
    }

    Decision decide(String message, AssistantConversationState conversation, AssistantClientContext clientContext,
                    AssistantClientEvent event, boolean reset) {
        long started = RecognitionTrace.tick();
        Decision decision = resolve(message, conversation, clientContext, event, reset);
        Outcome outcome = switch (decision.kind()) {
            case CURRENT_TURN -> Outcome.OBSERVED;
            case BLOCKED -> Outcome.BLOCKED;
            case CANDIDATE_SELECTION -> Outcome.SELECTED;
            case CONFIRMATION -> Outcome.CONTINUED;
            case SLOT_FILL -> Outcome.SLOT_FILLED;
            case CONTEXT_REPLY -> Outcome.OBSERVED;
        };
        RecognitionTrace.decision(Stage.SEMANTIC_DECISION, outcome, started, decision.response());
        return decision;
    }

    private Decision resolve(String message, AssistantConversationState conversation, AssistantClientContext clientContext,
                             AssistantClientEvent event, boolean reset) {
        long started = RecognitionTrace.tick();
        var collectionProposal = collections.propose(message, conversation);
        var navigationProposal = navigation.propose(message, conversation);
        boolean unsupported = constraints.handlesUnsupportedConstraint(message);
        String deterministicReason = unsupported ? "local_guard:unsupported_execution_constraint"
                : collectionProposal.isPresent() || navigationProposal.isPresent()
                ? "local_structure:deterministic_semantic_routing" : null;
        boolean polish = deterministicReason != null;
        RecognitionTrace.mark(Stage.ROUTING, polish ? Outcome.LOCAL : Outcome.OBSERVED, started);

        boolean confirm = confirmMessages.contains(normalizeControl(message));
        IntentRecognitionResponse proposal = recognition.recognize(message, conversation, clientContext, deterministicReason);
        if (confirm && isConfirmationAfterCancellation(proposal, conversation)) {
            IntentRecognitionResponse acknowledgement = restore(conversation, proposal, "用户确认或延续上一轮待处理意图。")
                    .withBackendActionDraft(BackendActionDraft.unsupportedAction());
            return new Decision(acknowledgement, Kind.BLOCKED, null, null, false);
        }
        if (unsupported && "non_executable_guard:unsupported_exclusion_remainder".equals(proposal.reason())) {
            proposal = reject(proposal, ExecutableConstraintGuard.EXCLUSION);
        }
        if (blocksContinuation(proposal)) {
            return new Decision(proposal, Kind.BLOCKED, null, null, polish || AssistantFlowPolicy.isCapabilityBoundary(proposal));
        }

        started = RecognitionTrace.tick();
        IntentRecognitionResponse collected = collectionProposal.isPresent() ? collectionProposal.get().apply(proposal) : proposal;
        traceRewrite(Stage.COLLECTION_ROUTING, proposal, collected, started);
        started = RecognitionTrace.tick();
        IntentRecognitionResponse navigated = AssistantFlowPolicy.isCapabilityBoundary(collected) ? collected
                : navigationProposal.map(candidate -> candidate.apply(collected)).orElse(collected);
        traceRewrite(Stage.NAVIGATION, collected, navigated, started);
        IntentRecognitionResponse base = validate(message, navigated, clientContext, true);
        if (blocksContinuation(base)) {
            return new Decision(base, Kind.BLOCKED, null, null, polish || AssistantFlowPolicy.isCapabilityBoundary(base));
        }

        started = RecognitionTrace.tick();
        boolean hasPendingAction = hasPendingAction(conversation);
        var selection = hasPendingAction && conversation.candidateBinding() != null
                && !conversation.candidateBinding().candidates().isEmpty() && !reset
                ? candidateSelection.select(conversation.candidateBinding(), message, event)
                : CandidateSelectionService.SelectionAttempt.notMatched();
        RecognitionTrace.mark(Stage.CANDIDATE_SELECTION, selection.matched() ? Outcome.SELECTED : Outcome.NO_MATCH, started);
        if (selection.matched()) {
            IntentRecognitionResponse selected = restore(conversation, base, "用户选择了上一轮候选。");
            if (!collections.isScopedCollection(selected)) {
                CandidateBindingResult binding = selection.candidateBinding();
                SemanticFrame frame = conversation.semanticFrame().forCandidateSelection(
                        binding.selectedCandidate(), selectedCandidateIndex(binding));
                selected = selected.withSemanticFrame(frame, selected.entities(), selected.actionDraft());
            }
            return validateContinuation(message, clientContext, selected, Kind.CANDIDATE_SELECTION,
                    selection.candidateBinding(), null, false);
        }

        started = RecognitionTrace.tick();
        var attempt = !collections.isScopedCollection(base)
                && "FOLLOW_UP".equals(base.semanticFrame().relation())
                && !List.of("SEARCH", "LIST_CHILDREN").contains(base.semanticFrame().operation())
                ? contextResolver.resolve(message, conversation, base, base.semanticFrame())
                : ConversationContextResolver.ContextAttempt.notApplied();
        var context = attempt.resolution();
        boolean rewrite = context != null && context.shouldRewrite();
        RecognitionTrace.decision(Stage.CONTEXT_RESOLUTION, !attempt.applied() ? Outcome.NO_MATCH
                : rewrite ? Outcome.REWRITTEN : Outcome.OBSERVED, started, attempt.response());
        if (attempt.applied() && attempt.response() != null) {
            if (!rewrite) {
                return new Decision(attempt.response(), Kind.CONTEXT_REPLY, null, context, false);
            }
            base = validate(message, attempt.response(), clientContext, true);
            if (blocksContinuation(base)) {
                return new Decision(base, Kind.BLOCKED, null, null, true);
            }
        }

        started = RecognitionTrace.tick();
        Kind kind = Kind.CURRENT_TURN;
        IntentRecognitionResponse response = base;
        if (confirm && hasPendingAction) {
            kind = Kind.CONFIRMATION;
            response = restore(conversation, base, "用户确认或延续上一轮待处理意图。");
        } else if (!confirm && !reset && hasPendingIntent(conversation) && conversation.hasPendingSlots()) {
            var resolution = pendingSlots.resolve(message, conversation, base);
            if (resolution.resolved() && shouldFillSlots(message, conversation, base)) {
                kind = Kind.SLOT_FILL;
                response = fillSlots(conversation, base, resolution);
            }
        }
        RecognitionTrace.decision(Stage.PENDING_INTENT, switch (kind) {
            case CONFIRMATION -> Outcome.CONTINUED;
            case SLOT_FILL -> Outcome.SLOT_FILLED;
            default -> Outcome.NO_MATCH;
        }, started, response);
        return kind == Kind.CURRENT_TURN ? new Decision(response, kind, null, context, polish)
                : validateContinuation(message, clientContext, response, kind, null, context, polish);
    }

    private Decision validateContinuation(String message, AssistantClientContext clientContext,
                                          IntentRecognitionResponse response, Kind kind, CandidateBindingResult selection,
                                          ConversationContextResolution context, boolean polish) {
        // Older clients omit capability metadata on follow-ups to an existing plan.
        boolean declaresCapabilities = !clientContext.actionContractVersion().isBlank()
                || !clientContext.supportedActionTypes().isEmpty();
        IntentRecognitionResponse validated = validate(message, response, clientContext, declaresCapabilities);
        return blocksContinuation(validated)
                ? new Decision(validated, Kind.BLOCKED, null, null, true)
                : new Decision(validated, kind, selection, context, polish);
    }

    private IntentRecognitionResponse validate(
            String message, IntentRecognitionResponse response, AssistantClientContext clientContext, boolean checkClient
    ) {
        long started = RecognitionTrace.tick();
        var rejection = constraints.evaluate(message, response);
        if (rejection.isPresent()) {
            response = reject(response, rejection.get());
        }
        RecognitionTrace.decision(Stage.CONSTRAINT_GUARD, rejection.isPresent() ? Outcome.BLOCKED : Outcome.NO_MATCH,
                started, response);
        if (checkClient && collections.isScopedCollection(response) && !clientContext.supportsAction(response.actionDraft().type())) {
            started = RecognitionTrace.tick();
            response = response.withCapabilityBoundary(
                    "client_action_contract_outdated",
                    "当前移动端版本还不能安全执行这种目录批量操作，请更新应用后再试。",
                    "更新移动端后，可使用目录内全部文件的批量移动和删除。"
            );
            RecognitionTrace.decision(Stage.CLIENT_CAPABILITY, Outcome.BLOCKED, started, response);
        }
        return response;
    }

    private static IntentRecognitionResponse reject(IntentRecognitionResponse response, ExecutableConstraintGuard.Rejection rejection) {
        return response.withCapabilityBoundary(rejection.reason(), rejection.guidance(), rejection.guidance());
    }

    private static boolean blocksContinuation(IntentRecognitionResponse response) {
        String reason = response.reason();
        return AssistantFlowPolicy.isCapabilityBoundary(response) || reason != null
                && (reason.startsWith("non_executable_guard:") || reason.startsWith("conversation_follow_up_guard:"));
    }

    private static boolean isConfirmationAfterCancellation(
            IntentRecognitionResponse response, AssistantConversationState conversation
    ) {
        return "non_executable_guard:confirmation_without_pending_operation".equals(response.reason())
                && "assistant_acknowledgement".equals(conversation.pendingIntentId())
                && "CANCELLATION".equals(conversation.semanticFrame().relation())
                && "RESPOND".equals(conversation.semanticFrame().operation())
                && conversation.pendingActionDraft() != null && "none".equals(conversation.pendingActionDraft().type());
    }

    private static boolean hasPendingAction(AssistantConversationState conversation) {
        return hasPendingIntent(conversation)
                && conversation.pendingActionDraft() != null && !"search".equals(conversation.pendingActionDraft().type());
    }

    private static boolean hasPendingIntent(AssistantConversationState conversation) {
        return conversation.pendingIntentId() != null && !conversation.pendingIntentId().isBlank();
    }

    private IntentRecognitionResponse restore(
            AssistantConversationState conversation, IntentRecognitionResponse response, String reason
    ) {
        var restored = recognition.rebuildForConversation(response, conversation.pendingIntentId(), conversation.entities(), reason);
        restored = restored.withSemanticFrame(conversation.semanticFrame(), restored.entities(), restored.actionDraft());
        return collections.restoreStored(restored, conversation);
    }

    private IntentRecognitionResponse fillSlots(AssistantConversationState conversation, IntentRecognitionResponse response,
                                               PendingSlotResolutionService.Resolution resolution) {
        Map<String, Object> merged = new LinkedHashMap<>(conversation.entities());
        merged.putAll(resolution.values());
        if (resolution.values().containsKey("target_name")) {
            for (String key : List.of("result_type", "file_type", "extension")) {
                Object value = response.entities().get(key);
                if (value != null && !String.valueOf(value).trim().isBlank()) {
                    merged.put(key, value);
                }
            }
        }
        var rebuilt = recognition.rebuildForConversation(response, conversation.pendingIntentId(), merged,
                "根据上一轮待补充槽位合并当前输入。");
        return collections.restoreStored(
                rebuilt.withSemanticFrame(resolution.semanticFrame(), rebuilt.entities(), rebuilt.actionDraft()), conversation);
    }

    private boolean shouldFillSlots(String message, AssistantConversationState conversation, IntentRecognitionResponse response) {
        var local = intentRouter.route(message);
        return "fallback".equals(local.intent())
                || conversation.pendingIntentId().equals(local.intent())
                && ("fallback".equals(response.intentId()) || conversation.pendingIntentId().equals(response.intentId())
                || !response.entities().isEmpty() || !local.entities().isEmpty());
    }

    private static Integer selectedCandidateIndex(CandidateBindingResult binding) {
        CandidateItem candidate = binding.selectedCandidate();
        int index = candidate == null ? -1 : binding.candidates().indexOf(candidate);
        return index < 0 ? null : index + 1;
    }

    private static void traceRewrite(Stage stage, IntentRecognitionResponse before, IntentRecognitionResponse after, long started) {
        RecognitionTrace.decision(stage, before == after ? Outcome.NO_MATCH : Outcome.REWRITTEN, started, after);
    }

    private static String normalizeControl(String message) {
        return message.trim().replaceAll("[\\s，。,.!?！？]+", "");
    }

    enum Kind { CURRENT_TURN, BLOCKED, CANDIDATE_SELECTION, CONTEXT_REPLY, SLOT_FILL, CONFIRMATION }

    record Decision(IntentRecognitionResponse response, Kind kind, CandidateBindingResult selectedBinding,
                    ConversationContextResolution contextResolution, boolean polishReply) {}
}
