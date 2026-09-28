package com.alicia.cloudstorage.ragexecution.infrastructure.persistence;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionStepStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "rag_execution_step",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_rag_execution_step_index",
                        columnNames = {"execution_id", "step_index"}
                ),
                @UniqueConstraint(
                        name = "uk_rag_execution_step_key",
                        columnNames = {"execution_id", "step_key"}
                )
        },
        indexes = @Index(name = "idx_rag_execution_step_due", columnList = "status,available_at")
)
public class ExecutionStepJpaEntity {

    @Id
    @Column(nullable = false, length = 36, columnDefinition = "char(36)")
    private String id;

    @Column(name = "execution_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String executionId;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(name = "step_key", nullable = false, length = 64)
    private String stepKey;

    @Column(name = "depends_on_keys", nullable = false, length = 650)
    private String dependsOnKeys;

    @Column(name = "output_key", length = 64)
    private String outputKey;

    @Column(name = "required_client_fields", nullable = false, length = 255)
    private String requiredClientFields;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private ExecutionActionType actionType;

    @Column(name = "payload_schema_version", nullable = false, length = 32)
    private String payloadSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private JsonNode payload;

    @Column(name = "payload_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String payloadHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExecutionStepStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "json")
    private JsonNode result;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message_safe", length = 1000)
    private String errorMessageSafe;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExecutionStepJpaEntity() {
    }

    public static ExecutionStepJpaEntity createPending(
            UUID id,
            UUID executionId,
            int stepIndex,
            ExecutionActionType actionType,
            String payloadSchemaVersion,
            JsonNode payload,
            String payloadHash,
            Instant now
    ) {
        return createPending(
                id, executionId, stepIndex, "step_" + stepIndex, actionType,
                payloadSchemaVersion, payload, payloadHash, java.util.List.of(), "",
                java.util.List.of(), now
        );
    }

    public static ExecutionStepJpaEntity createPending(
            UUID id,
            UUID executionId,
            int stepIndex,
            String stepKey,
            ExecutionActionType actionType,
            String payloadSchemaVersion,
            JsonNode payload,
            String payloadHash,
            java.util.List<String> dependsOn,
            String outputKey,
            java.util.List<String> requiredClientFields,
            Instant now
    ) {
        if (id == null || executionId == null || actionType == null || payload == null || now == null) {
            throw new IllegalArgumentException("Execution step identity, action, payload, and time are required.");
        }
        if (stepIndex < 0) {
            throw new IllegalArgumentException("stepIndex must not be negative.");
        }
        if (payloadSchemaVersion == null || payloadSchemaVersion.isBlank() || payloadSchemaVersion.length() > 32) {
            throw new IllegalArgumentException("payloadSchemaVersion is invalid.");
        }
        if (payloadHash == null || !payloadHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadHash must be a lowercase SHA-256 digest.");
        }
        ExecutionStepJpaEntity entity = new ExecutionStepJpaEntity();
        entity.id = id.toString();
        entity.executionId = executionId.toString();
        entity.stepIndex = stepIndex;
        entity.stepKey = requireIdentifier(stepKey, "stepKey", false);
        entity.dependsOnKeys = joinIdentifiers(dependsOn, "dependsOn", false);
        entity.outputKey = requireIdentifier(outputKey, "outputKey", true);
        entity.requiredClientFields = joinIdentifiers(requiredClientFields, "requiredClientFields", false);
        entity.actionType = actionType;
        entity.payloadSchemaVersion = payloadSchemaVersion.trim();
        entity.payload = payload.deepCopy();
        entity.payloadHash = payloadHash;
        entity.status = ExecutionStepStatus.PENDING;
        entity.attempts = 0;
        entity.availableAt = now;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void start(Instant now) {
        requireTime(now);
        if (status != ExecutionStepStatus.PENDING && status != ExecutionStepStatus.RETRY_WAIT) {
            throw new IllegalStateException("Only a pending or retryable step can start.");
        }
        if (availableAt.isAfter(now)) {
            throw new IllegalStateException("Step is not available yet.");
        }
        status = ExecutionStepStatus.RUNNING;
        attempts++;
        if (startedAt == null) {
            startedAt = now;
        }
        updatedAt = now;
        errorCode = null;
        errorMessageSafe = null;
    }

    public void recoverRunningAttempt(Instant now) {
        requireTime(now);
        if (status != ExecutionStepStatus.RUNNING) {
            throw new IllegalStateException("Only a running step can be recovered.");
        }
        updatedAt = now;
    }

    public void succeed(JsonNode result, Instant now) {
        requireRunning(now);
        if (result == null) {
            throw new IllegalArgumentException("result is required.");
        }
        this.result = result.deepCopy();
        this.status = ExecutionStepStatus.SUCCEEDED;
        this.finishedAt = now;
        this.updatedAt = now;
        this.errorCode = null;
        this.errorMessageSafe = null;
    }

    public void retry(String errorCode, String safeMessage, Instant availableAt, Instant now) {
        requireRunning(now);
        if (availableAt == null || !availableAt.isAfter(now)) {
            throw new IllegalArgumentException("availableAt must be after now.");
        }
        this.status = ExecutionStepStatus.RETRY_WAIT;
        this.errorCode = requireText(errorCode, 64, "errorCode");
        this.errorMessageSafe = normalizeOptional(safeMessage, 1000, "safeMessage");
        this.availableAt = availableAt;
        this.updatedAt = now;
    }

    public void fail(String errorCode, String safeMessage, Instant now) {
        requireRunning(now);
        this.status = ExecutionStepStatus.FAILED;
        this.errorCode = requireText(errorCode, 64, "errorCode");
        this.errorMessageSafe = normalizeOptional(safeMessage, 1000, "safeMessage");
        this.finishedAt = now;
        this.updatedAt = now;
    }

    public void waitForClientInput(JsonNode request, Instant now) {
        requireRunning(now);
        if (request == null || !request.isObject()) {
            throw new IllegalArgumentException("client input request is required.");
        }
        this.result = request.deepCopy();
        this.status = ExecutionStepStatus.WAITING_CLIENT_INPUT;
        this.updatedAt = now;
    }

    public void completeClientInput(JsonNode clientResult, Instant now) {
        requireTime(now);
        if (status != ExecutionStepStatus.WAITING_CLIENT_INPUT) {
            throw new IllegalStateException("Only a client-input step can be completed.");
        }
        if (clientResult == null || !clientResult.isObject()) {
            throw new IllegalArgumentException("client result is required.");
        }
        this.result = clientResult.deepCopy();
        this.status = ExecutionStepStatus.SUCCEEDED;
        this.finishedAt = now;
        this.updatedAt = now;
        this.errorCode = null;
        this.errorMessageSafe = null;
    }

    public void failClientInput(String errorCode, String safeMessage, Instant now) {
        requireTime(now);
        if (status != ExecutionStepStatus.WAITING_CLIENT_INPUT) {
            throw new IllegalStateException("Only a client-input step can fail.");
        }
        this.status = ExecutionStepStatus.FAILED;
        this.errorCode = requireText(errorCode, 64, "errorCode");
        this.errorMessageSafe = normalizeOptional(safeMessage, 1000, "safeMessage");
        this.finishedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return UUID.fromString(id);
    }

    public UUID getExecutionId() {
        return UUID.fromString(executionId);
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public String getStepKey() {
        return stepKey;
    }

    public java.util.List<String> getDependsOn() {
        return splitIdentifiers(dependsOnKeys);
    }

    public String getOutputKey() {
        return outputKey == null ? "" : outputKey;
    }

    public java.util.List<String> getRequiredClientFields() {
        return splitIdentifiers(requiredClientFields);
    }

    public ExecutionActionType getActionType() {
        return actionType;
    }

    public ExecutionStepStatus getStatus() {
        return status;
    }

    public JsonNode getPayload() {
        return payload.deepCopy();
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public String getPayloadSchemaVersion() {
        return payloadSchemaVersion;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public JsonNode getResult() {
        return result == null ? null : result.deepCopy();
    }

    public String getErrorCode() {
        return errorCode;
    }

    private void requireRunning(Instant now) {
        requireTime(now);
        if (status != ExecutionStepStatus.RUNNING) {
            throw new IllegalStateException("Only a running step can be completed.");
        }
    }

    private static void requireTime(Instant value) {
        if (value == null) {
            throw new IllegalArgumentException("now is required.");
        }
    }

    private static String requireText(String value, int maximumLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maximumLength + " characters.");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maximumLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, maximumLength, field);
    }

    private static String requireIdentifier(String value, String field, boolean optional) {
        if (value == null || value.isBlank()) {
            if (optional) {
                return null;
            }
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = value.trim();
        String pattern = "outputKey".equals(field)
                ? "[a-z][A-Za-z0-9_]{0,63}"
                : "[a-z][a-z0-9_]{0,63}";
        if (!normalized.matches(pattern)) {
            throw new IllegalArgumentException(field + " is not a safe identifier.");
        }
        return normalized;
    }

    private static String joinIdentifiers(java.util.List<String> values, String field, boolean optional) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        java.util.List<String> normalized = values.stream()
                .map(value -> requireIdentifier(value, field, optional))
                .toList();
        return String.join(",", normalized);
    }

    private static java.util.List<String> splitIdentifiers(String value) {
        if (value == null || value.isBlank()) {
            return java.util.List.of();
        }
        return java.util.List.of(value.split(","));
    }
}
