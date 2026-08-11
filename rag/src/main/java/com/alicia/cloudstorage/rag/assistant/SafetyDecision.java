package com.alicia.cloudstorage.rag.assistant;

public record SafetyDecision(
        String risk,
        boolean requiresConfirmation,
        boolean allowedToExecute,
        String reason
) {
}
