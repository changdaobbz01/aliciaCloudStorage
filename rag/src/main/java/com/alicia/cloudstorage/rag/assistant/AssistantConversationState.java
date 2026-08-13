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
        Instant expiresAt
) {
    public AssistantConversationState {
        entities = entities == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(entities));
        pendingSlots = pendingSlots == null ? List.of() : List.copyOf(pendingSlots);
        focus = focus == null ? AssistantConversationFocus.empty() : focus;
        semanticFrame = semanticFrame == null ? SemanticFrame.empty() : semanticFrame;
        authorizationFingerprint = authorizationFingerprint == null ? "" : authorizationFingerprint;
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
