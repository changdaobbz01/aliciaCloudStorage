package com.alicia.cloudstorage.rag.assistant;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AssistantConversationState(
        String conversationId,
        int turnIndex,
        String pendingIntentId,
        Map<String, Object> entities,
        List<String> pendingSlots,
        ActionDraft pendingActionDraft,
        ActionPlan pendingActionPlan,
        CandidateBindingResult candidateBinding,
        AssistantConversationFocus focus,
        SemanticFrame semanticFrame,
        String authorizationFingerprint,
        Instant expiresAt,
        List<AssistantConversationTurn> recentTurns
) {
    public AssistantConversationState(
            String conversationId, int turnIndex, String pendingIntentId, Map<String, Object> entities,
            List<String> pendingSlots, ActionDraft pendingActionDraft, ActionPlan pendingActionPlan,
            CandidateBindingResult candidateBinding, AssistantConversationFocus focus, SemanticFrame semanticFrame,
            String authorizationFingerprint, Instant expiresAt
    ) {
        this(conversationId, turnIndex, pendingIntentId, entities, pendingSlots, pendingActionDraft,
                pendingActionPlan, candidateBinding, focus, semanticFrame, authorizationFingerprint, expiresAt, List.of());
    }

    public AssistantConversationState {
        entities = entities == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(entities));
        pendingSlots = pendingSlots == null ? List.of() : List.copyOf(pendingSlots);
        focus = focus == null ? AssistantConversationFocus.empty() : focus;
        semanticFrame = semanticFrame == null ? SemanticFrame.empty() : semanticFrame;
        authorizationFingerprint = authorizationFingerprint == null ? "" : authorizationFingerprint;
        recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
    }

    AssistantConversationState withoutAuthorizationFingerprint() {
        if (authorizationFingerprint.isEmpty()) {
            return this;
        }
        return new AssistantConversationState(conversationId, turnIndex, pendingIntentId, entities, pendingSlots,
                pendingActionDraft, pendingActionPlan, candidateBinding, focus, semanticFrame, "", expiresAt, recentTurns);
    }

    boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    boolean hasPendingSlots() {
        return pendingSlots != null && !pendingSlots.isEmpty();
    }

    AssistantConversationSnapshot snapshot(String status) {
        return new AssistantConversationSnapshot(
                conversationId,
                turnIndex,
                status,
                pendingIntentId,
                pendingSlots,
                pendingActionDraft != null && pendingActionDraft.needsBackendBinding(),
                candidateBinding == null ? "" : candidateBinding.status(),
                candidateBinding == null ? 0 : candidateBinding.candidates().size(),
                expiresAt
        );
    }
}
