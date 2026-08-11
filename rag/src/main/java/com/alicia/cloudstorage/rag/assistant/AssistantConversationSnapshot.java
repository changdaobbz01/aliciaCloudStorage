package com.alicia.cloudstorage.rag.assistant;

import java.time.Instant;
import java.util.List;

public record AssistantConversationSnapshot(
        String conversationId,
        int turnIndex,
        String status,
        String pendingIntentId,
        List<String> pendingSlots,
        boolean hasPendingAction,
        String candidateBindingStatus,
        int candidateCount,
        Instant expiresAt
) {
}
