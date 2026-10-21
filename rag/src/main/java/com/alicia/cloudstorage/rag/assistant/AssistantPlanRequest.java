package com.alicia.cloudstorage.rag.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AssistantPlanRequest(
        @NotBlank @Size(max = 4096) String message,
        @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._-]*") String conversationId,
        @Valid AssistantClientContext clientContext,
        @Valid AssistantClientEvent clientEvent
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
