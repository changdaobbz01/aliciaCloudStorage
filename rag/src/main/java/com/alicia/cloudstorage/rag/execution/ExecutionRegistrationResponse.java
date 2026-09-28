package com.alicia.cloudstorage.rag.execution;

import java.time.Instant;

public record ExecutionRegistrationResponse(
        String executionId,
        String status,
        long version,
        Instant expiresAt
) {
}
