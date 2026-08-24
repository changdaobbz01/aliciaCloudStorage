package com.alicia.cloudstorage.api.identity;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Service
public class IdentityGatewayTelemetry {

    private final ConcurrentMap<String, OperationState> operations = new ConcurrentHashMap<>();

    public <T> T observe(String operation, Supplier<T> action) {
        long startedAt = System.nanoTime();
        try {
            T result = action.get();
            record(operation, true, null, startedAt);
            return result;
        } catch (RuntimeException ex) {
            record(operation, false, ex, startedAt);
            throw ex;
        }
    }

    public List<IdentityGatewayOperationSnapshot> snapshots() {
        return operations.entrySet().stream()
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .sorted(Comparator.comparing(IdentityGatewayOperationSnapshot::operation))
                .toList();
    }

    private void record(String operation, boolean success, RuntimeException error, long startedAt) {
        long durationMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        operations.computeIfAbsent(operation, ignored -> new OperationState())
                .record(success, error, durationMs);
    }

    private static final class OperationState {

        private long successCount;
        private long failureCount;
        private String lastOutcome = "none";
        private String lastError;
        private long lastDurationMs;
        private LocalDateTime lastObservedAt;

        synchronized void record(boolean success, RuntimeException error, long durationMs) {
            if (success) {
                successCount++;
                lastOutcome = "success";
                lastError = null;
            } else {
                failureCount++;
                lastOutcome = "failure";
                lastError = error.getClass().getSimpleName();
            }
            lastDurationMs = durationMs;
            lastObservedAt = LocalDateTime.now();
        }

        synchronized IdentityGatewayOperationSnapshot snapshot(String operation) {
            return new IdentityGatewayOperationSnapshot(
                    operation,
                    successCount,
                    failureCount,
                    lastOutcome,
                    lastError,
                    lastDurationMs,
                    lastObservedAt
            );
        }
    }
}
