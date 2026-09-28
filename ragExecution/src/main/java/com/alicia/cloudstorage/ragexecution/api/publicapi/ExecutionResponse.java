package com.alicia.cloudstorage.ragexecution.api.publicapi;

import java.time.Instant;
import java.util.List;

public record ExecutionResponse(
        String executionId,
        String status,
        long version,
        String summary,
        String risk,
        Instant expiresAt,
        Instant confirmedAt,
        Instant queuedAt,
        Instant startedAt,
        Instant finishedAt,
        String resultCode,
        Object result,
        String errorCode,
        List<ExecutionStepResponse> steps
) {
}
