package com.alicia.cloudstorage.rag.assistant;

public record AssistantPlanRequest(
        String message,
        String conversationId,
        AssistantClientContext clientContext,
        AssistantClientEvent clientEvent
) {
    public AssistantPlanRequest(String message, String conversationId) {
        this(message, conversationId, AssistantClientContext.empty(), AssistantClientEvent.none());
    }

    public AssistantPlanRequest(
            String message,
            String conversationId,
            AssistantClientContext clientContext
    ) {
        this(message, conversationId, clientContext, AssistantClientEvent.none());
    }

    public AssistantPlanRequest {
        clientContext = clientContext == null ? AssistantClientContext.empty() : clientContext;
        clientEvent = clientEvent == null ? AssistantClientEvent.none() : clientEvent;
    }
}
