package com.alicia.cloudstorage.rag.assistant;

import java.time.Instant;

public record ExecutionReference(
        String executionId,
        String status,
        long version,
        Instant expiresAt
) {
}
