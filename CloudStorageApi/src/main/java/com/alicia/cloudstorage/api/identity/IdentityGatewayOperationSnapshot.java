package com.alicia.cloudstorage.api.identity;

import java.time.LocalDateTime;

public record IdentityGatewayOperationSnapshot(
        String operation,
        long successCount,
        long failureCount,
        String lastOutcome,
        String lastError,
        long lastDurationMs,
        LocalDateTime lastObservedAt
) {
}
