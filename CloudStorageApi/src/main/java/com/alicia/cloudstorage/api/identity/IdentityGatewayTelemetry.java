package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.principal.PrincipalAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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

        synchronized IdentityGatewayOperationSnapshot snapshot(String operation) {
            long totalCount = successCount + failureCount;
            long averageDurationMs = totalCount == 0L ? 0L : totalDurationMs / totalCount;
            return new IdentityGatewayOperationSnapshot(
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
            if (error instanceof PrincipalAccessException) {
                return "access_denied";
            }
            if (error instanceof IdentityServiceUnavailableException) {
                return "identity_unavailable";
            }
            if (error instanceof ResourceAccessException) {
                return "network_error";
            }
            if (error instanceof RestClientResponseException responseException) {
                if (responseException.getStatusCode().is5xxServerError()) {
                    return "identity_server_error";
                }
                if (responseException.getStatusCode().is4xxClientError()) {
                    return "identity_client_error";
                }
                return "identity_http_error";
            }
            if (error instanceof RestClientException) {
                return "identity_client_error";
            }
            if (error instanceof IllegalArgumentException) {
                return "data_error";
            }

            return "runtime_error";
        }
    }
}
