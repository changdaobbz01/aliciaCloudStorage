package com.alicia.cloudstorage.ragexecution.infrastructure.persistence;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionRisk;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionStateMachine;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "rag_execution",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_rag_execution_owner_idempotency",
                        columnNames = {"owner_user_id", "idempotency_key"}
                ),
                @UniqueConstraint(
                        name = "uk_rag_execution_owner_plan",
                        columnNames = {"owner_user_id", "plan_id", "plan_hash"}
                ),
                @UniqueConstraint(
                        name = "uk_rag_execution_owner_plan_id",
                        columnNames = {"owner_user_id", "plan_id"}
                )
        },
        indexes = {
                @Index(name = "idx_rag_execution_status_lease", columnList = "status,lease_until,expires_at,queued_at"),
                @Index(name = "idx_rag_execution_owner_created", columnList = "owner_user_id,created_at")
        }
)
public class ExecutionJpaEntity {

    @Id
    @Column(nullable = false, length = 36, columnDefinition = "char(36)")
    private String id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "conversation_id", nullable = false, length = 128)
    private String conversationId;

    @Column(name = "source_response_id", nullable = false, length = 128)
    private String sourceResponseId;

    @Column(name = "plan_id", nullable = false, length = 128)
    private String planId;

    @Column(name = "plan_schema_version", nullable = false, length = 32)
    private String planSchemaVersion;

    @Column(name = "plan_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String planHash;

    @Column(name = "action_summary", nullable = false, length = 1000)
    private String actionSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExecutionRisk risk;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExecutionStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "queued_at")
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "result_code", length = 64)
    private String resultCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_summary_json", columnDefinition = "json")
    private JsonNode resultSummary;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message_safe", length = 1000)
    private String errorMessageSafe;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExecutionJpaEntity() {
    }

    public static ExecutionJpaEntity createPending(
            UUID id,
            long ownerUserId,
            String conversationId,
            String sourceResponseId,
            String planId,
            String planSchemaVersion,
            String planHash,
            String actionSummary,
            ExecutionRisk risk,
            String idempotencyKey,
            Instant expiresAt,
            Instant now
    ) {
        ExecutionJpaEntity entity = new ExecutionJpaEntity();
        entity.id = requireId(id);
        entity.ownerUserId = requirePositive(ownerUserId, "ownerUserId");
        entity.conversationId = requireText(conversationId, 128, "conversationId");
        entity.sourceResponseId = requireText(sourceResponseId, 128, "sourceResponseId");
        entity.planId = requireText(planId, 128, "planId");
        entity.planSchemaVersion = requireText(planSchemaVersion, 32, "planSchemaVersion");
        entity.planHash = requireHash(planHash, "planHash");
        entity.actionSummary = requireText(actionSummary, 1000, "actionSummary");
        entity.risk = requireValue(risk, "risk");
        entity.status = ExecutionStatus.PENDING_CONFIRMATION;
        entity.idempotencyKey = requireText(idempotencyKey, 128, "idempotencyKey");
        entity.expiresAt = requireValue(expiresAt, "expiresAt");
        entity.createdAt = requireValue(now, "now");
        entity.updatedAt = now;
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt must be after now.");
        }
        return entity;
    }

    public void transitionTo(ExecutionStatus target, Instant now) {
        Instant transitionTime = requireValue(now, "now");
        ExecutionStateMachine.requireTransition(status, target);
        status = target;
        updatedAt = transitionTime;

        if (target == ExecutionStatus.QUEUED) {
            queuedAt = transitionTime;
            if (confirmedAt == null) {
                confirmedAt = transitionTime;
            }
        } else if (target == ExecutionStatus.RUNNING && startedAt == null) {
            startedAt = transitionTime;
        }

        if (target.isTerminal()) {
            finishedAt = transitionTime;
            clearLease();
        } else if (target == ExecutionStatus.RETRY_WAIT
                || target == ExecutionStatus.WAITING_CLIENT_INPUT
                || target == ExecutionStatus.QUEUED) {
            clearLease();
        }
    }

    public void confirmAndQueue(Instant now, Instant queueDeadline) {
        Instant confirmationTime = requireValue(now, "now");
        Instant deadline = requireValue(queueDeadline, "queueDeadline");
        if (!expiresAt.isAfter(confirmationTime)) {
            throw new IllegalStateException("Execution confirmation has expired.");
        }
        if (!deadline.isAfter(confirmationTime)) {
            throw new IllegalArgumentException("queueDeadline must be after now.");
        }
        transitionTo(ExecutionStatus.QUEUED, confirmationTime);
        if (deadline.isBefore(expiresAt)) {
            expiresAt = deadline;
        }
    }

    public void claimLease(String owner, Instant now, Instant until) {
        String normalizedOwner = requireText(owner, 128, "leaseOwner");
        Instant claimTime = requireValue(now, "now");
        Instant leaseEnd = requireValue(until, "leaseUntil");
        if (!leaseEnd.isAfter(claimTime)) {
            throw new IllegalArgumentException("leaseUntil must be after now.");
        }
        if (!expiresAt.isAfter(claimTime)) {
            throw new IllegalStateException("Expired execution cannot be leased.");
        }
        transitionTo(ExecutionStatus.RUNNING, claimTime);
        leaseOwner = normalizedOwner;
        leaseUntil = leaseEnd;
        heartbeatAt = claimTime;
    }

    public void reclaimLease(String owner, Instant now, Instant until) {
        String normalizedOwner = requireText(owner, 128, "leaseOwner");
        Instant claimTime = requireValue(now, "now");
        Instant leaseEnd = requireValue(until, "leaseUntil");
        if (status != ExecutionStatus.RUNNING || (leaseUntil != null && !leaseUntil.isBefore(claimTime))) {
            throw new IllegalStateException("Execution does not have an expired running lease.");
        }
        if (!expiresAt.isAfter(claimTime)) {
            throw new IllegalStateException("Expired execution cannot be reclaimed.");
        }
        if (!leaseEnd.isAfter(claimTime)) {
            throw new IllegalArgumentException("leaseUntil must be after now.");
        }
        leaseOwner = normalizedOwner;
        leaseUntil = leaseEnd;
        heartbeatAt = claimTime;
        updatedAt = claimTime;
    }

    public void renewLease(String owner, Instant now, Instant until) {
        String normalizedOwner = requireText(owner, 128, "leaseOwner");
        Instant heartbeatTime = requireValue(now, "now");
        Instant leaseEnd = requireValue(until, "leaseUntil");
        if (status != ExecutionStatus.RUNNING || !normalizedOwner.equals(leaseOwner)) {
            throw new IllegalStateException("Execution lease is not owned by this worker.");
        }
        if (!leaseEnd.isAfter(heartbeatTime)) {
            throw new IllegalArgumentException("leaseUntil must be after now.");
        }
        leaseUntil = leaseEnd;
        heartbeatAt = heartbeatTime;
        updatedAt = heartbeatTime;
    }

    public void complete(String resultCode, JsonNode resultSummary, Instant now) {
        this.resultCode = requireText(resultCode, 64, "resultCode");
        this.resultSummary = resultSummary == null ? null : resultSummary.deepCopy();
        this.errorCode = null;
        this.errorMessageSafe = null;
        transitionTo(ExecutionStatus.SUCCEEDED, now);
    }

    public void completePartially(String resultCode, JsonNode resultSummary, String errorCode, Instant now) {
        this.resultCode = requireText(resultCode, 64, "resultCode");
        this.resultSummary = resultSummary == null ? null : resultSummary.deepCopy();
        this.errorCode = requireText(errorCode, 64, "errorCode");
        this.errorMessageSafe = "One or more workflow steps did not complete.";
        transitionTo(ExecutionStatus.PARTIALLY_SUCCEEDED, now);
    }

    public void queueNextStep(Instant now, Instant queueDeadline) {
        Instant nextDeadline = requireValue(queueDeadline, "queueDeadline");
        if (!nextDeadline.isAfter(now)) {
            throw new IllegalArgumentException("queueDeadline must be after now.");
        }
        transitionTo(ExecutionStatus.QUEUED, now);
        expiresAt = nextDeadline;
    }

    public void waitForClientInput(Instant now, Instant clientInputDeadline) {
        Instant deadline = requireValue(clientInputDeadline, "clientInputDeadline");
        if (!deadline.isAfter(now)) {
            throw new IllegalArgumentException("clientInputDeadline must be after now.");
        }
        transitionTo(ExecutionStatus.WAITING_CLIENT_INPUT, now);
        expiresAt = deadline;
    }

    public void fail(String errorCode, String safeMessage, Instant now) {
        this.errorCode = requireText(errorCode, 64, "errorCode");
        this.errorMessageSafe = normalizeOptional(safeMessage, 1000, "safeMessage");
        transitionTo(ExecutionStatus.FAILED, now);
    }

    public void waitForRetry(String errorCode, String safeMessage, Instant now) {
        this.errorCode = requireText(errorCode, 64, "errorCode");
        this.errorMessageSafe = normalizeOptional(safeMessage, 1000, "safeMessage");
        transitionTo(ExecutionStatus.RETRY_WAIT, now);
    }

    public UUID getId() {
        return UUID.fromString(id);
    }

    String getPersistenceId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getPlanId() {
        return planId;
    }

    public String getPlanHash() {
        return planHash;
    }

    public String getActionSummary() {
        return actionSummary;
    }

    public ExecutionRisk getRisk() {
        return risk;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public String getResultCode() {
        return resultCode;
    }

    public JsonNode getResultSummary() {
        return resultSummary == null ? null : resultSummary.deepCopy();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessageSafe() {
        return errorMessageSafe;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void beforeInsert() {
        if (status == null || createdAt == null || updatedAt == null) {
            throw new IllegalStateException("Execution must be created through createPending().");
        }
    }

    @PreUpdate
    void beforeUpdate() {
        if (status == null || updatedAt == null) {
            throw new IllegalStateException("Execution state is incomplete.");
        }
    }

    private void clearLease() {
        leaseOwner = null;
        leaseUntil = null;
        heartbeatAt = null;
    }

    private static String requireId(UUID value) {
        return requireValue(value, "id").toString();
    }

    private static long requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
        return value;
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

    private static String requireHash(String value, String field) {
        String normalized = requireText(value, 64, field).toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 hex digest.");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maximumLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maximumLength + " characters.");
        }
        return normalized;
    }

    private static <T> T requireValue(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }
}
