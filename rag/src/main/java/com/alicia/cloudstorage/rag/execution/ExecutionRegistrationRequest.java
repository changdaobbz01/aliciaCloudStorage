package com.alicia.cloudstorage.rag.execution;

import java.time.Instant;
import java.util.List;

public record ExecutionRegistrationRequest(
        String planSchemaVersion,
        String sourceResponseId,
        String conversationId,
        String intentId,
        String planId,
        String planHash,
        String risk,
        String summary,
        List<ExecutionRegistrationStep> steps,
        Instant expiresAt
) {
}
