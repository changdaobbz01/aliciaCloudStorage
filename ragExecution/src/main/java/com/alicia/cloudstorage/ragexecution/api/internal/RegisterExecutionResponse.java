package com.alicia.cloudstorage.ragexecution.api.internal;

import java.time.Instant;

public record RegisterExecutionResponse(
        String executionId,
        String status,
        long version,
        Instant expiresAt
) {
}
