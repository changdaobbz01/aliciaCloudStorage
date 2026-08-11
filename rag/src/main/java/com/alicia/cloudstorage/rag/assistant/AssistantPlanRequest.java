package com.alicia.cloudstorage.rag.assistant;

public record AssistantPlanRequest(
        String message,
        String conversationId
) {
}
