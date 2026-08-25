package com.alicia.cloudstorage.rag.health;

import java.time.LocalDateTime;

public record RagDependencyOperationSnapshot(
        String operation,
        long successCount,
        long failureCount,
        long totalCount,
        long consecutiveFailureCount,
        String lastOutcome,
        String lastError,
        String lastErrorCategory,
        long lastDurationMs,
        long averageDurationMs,
        long maxDurationMs,
        LocalDateTime lastObservedAt,
        LocalDateTime lastSuccessAt,
        LocalDateTime lastFailureAt
) {
}
