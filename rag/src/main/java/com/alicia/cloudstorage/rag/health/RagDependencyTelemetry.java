package com.alicia.cloudstorage.rag.health;

import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpTimeoutException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Service
public class RagDependencyTelemetry {

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

    public List<RagDependencyOperationSnapshot> snapshots() {
        return snapshots("");
    }

    public List<RagDependencyOperationSnapshot> snapshots(String operationPrefix) {
        String prefix = operationPrefix == null ? "" : operationPrefix.trim();
        return operations.entrySet().stream()
                .filter(entry -> prefix.isBlank() || entry.getKey().startsWith(prefix))
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .sorted(Comparator.comparing(RagDependencyOperationSnapshot::operation))
                .toList();
    }

    private void record(String operation, boolean success, RuntimeException error, long startedAt) {
        long durationMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        operations.computeIfAbsent(normalizeOperation(operation), ignored -> new OperationState())
                .record(success, error, durationMs);
    }

    private static String normalizeOperation(String operation) {
        String normalized = operation == null ? "" : operation.trim();
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private static final class OperationState {

        private long successCount;
        private long failureCount;
        private long consecutiveFailureCount;
        private long totalDurationMs;
        private long maxDurationMs;
        private String lastOutcome = "none";
        private String lastError;
        private String lastErrorCategory;
        private long lastDurationMs;
        private LocalDateTime lastObservedAt;
        private LocalDateTime lastSuccessAt;
        private LocalDateTime lastFailureAt;

        synchronized void record(boolean success, RuntimeException error, long durationMs) {
            LocalDateTime observedAt = LocalDateTime.now();
            if (success) {
                successCount++;
                consecutiveFailureCount = 0L;
                lastOutcome = "success";
                lastError = null;
                lastErrorCategory = null;
                lastSuccessAt = observedAt;
            } else {
                failureCount++;
                consecutiveFailureCount++;
                lastOutcome = "failure";
                lastError = error.getClass().getSimpleName();
                lastErrorCategory = categorize(error);
                lastFailureAt = observedAt;
            }
            lastDurationMs = durationMs;
            totalDurationMs += durationMs;
            maxDurationMs = Math.max(maxDurationMs, durationMs);
            lastObservedAt = observedAt;
        }

        synchronized RagDependencyOperationSnapshot snapshot(String operation) {
            long totalCount = successCount + failureCount;
            long averageDurationMs = totalCount == 0L ? 0L : totalDurationMs / totalCount;
            return new RagDependencyOperationSnapshot(
                    operation,
                    successCount,
                    failureCount,
                    totalCount,
                    consecutiveFailureCount,
                    lastOutcome,
                    lastError,
                    lastErrorCategory,
                    lastDurationMs,
                    averageDurationMs,
                    maxDurationMs,
                    lastObservedAt,
                    lastSuccessAt,
                    lastFailureAt
            );
        }

        private static String categorize(RuntimeException error) {
            if (error instanceof ResponseStatusException responseException) {
                if (responseException.getStatusCode().is4xxClientError()) {
                    return "access_denied";
                }
                if (responseException.getStatusCode().is5xxServerError()) {
                    return "dependency_unavailable";
                }
                return "dependency_http_error";
            }
            if (error instanceof ResourceAccessException) {
                return "network_error";
            }
            if (error instanceof RestClientResponseException responseException) {
                if (responseException.getStatusCode().is5xxServerError()) {
                    return "dependency_server_error";
                }
                if (responseException.getStatusCode().is4xxClientError()) {
                    return "dependency_client_error";
                }
                return "dependency_http_error";
            }
            if (error instanceof RestClientException) {
                return "dependency_client_error";
            }
            if (hasCause(error, HttpTimeoutException.class)) {
                return "timeout";
            }
            if (error instanceof IllegalArgumentException) {
                return "data_error";
            }
            if (error instanceof IllegalStateException) {
                return "dependency_error";
            }

            return "runtime_error";
        }

        private static boolean hasCause(Throwable error, Class<? extends Throwable> causeType) {
            Throwable cursor = error;
            while (cursor != null) {
                if (causeType.isInstance(cursor)) {
                    return true;
                }
                cursor = cursor.getCause();
            }
            return false;
        }
    }
}
