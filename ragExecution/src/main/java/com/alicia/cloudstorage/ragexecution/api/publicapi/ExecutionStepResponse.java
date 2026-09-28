package com.alicia.cloudstorage.ragexecution.api.publicapi;

import java.time.Instant;

public record ExecutionStepResponse(
        String stepId,
        int index,
        String actionType,
        String status,
        int attempts,
        Instant startedAt,
        Instant finishedAt,
        String errorCode,
        Object result
) {
}
