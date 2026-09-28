package com.alicia.cloudstorage.ragexecution.api.internal;

import java.time.Instant;
import java.util.List;

public record RegisterExecutionRequest(
        String planSchemaVersion,
        String sourceResponseId,
        String conversationId,
        String intentId,
        String planId,
        String planHash,
        String risk,
        String summary,
        List<RegisterExecutionStepRequest> steps,
        Instant expiresAt
) {
    public RegisterExecutionRequest {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
