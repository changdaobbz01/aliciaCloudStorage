package com.alicia.cloudstorage.api.ragexecution.persistence;

import com.alicia.cloudstorage.api.ragexecution.domain.CloudActionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "rag_execution_action_receipt",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rag_action_receipt_execution_step",
                columnNames = {"execution_id", "step_id"}
        )
)
public class RagActionReceiptEntity {

    @Id
    @Column(name = "step_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String stepId;

    @Column(name = "execution_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String executionId;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private CloudActionType actionType;

    @Column(name = "payload_schema_version", nullable = false, length = 32)
    private String payloadSchemaVersion;

    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RagActionReceiptStatus status;

    @Column(name = "result_code", length = 64)
    private String resultCode;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected RagActionReceiptEntity() {
    }

    public static RagActionReceiptEntity begin(
            String executionId,
            String stepId,
            long actorUserId,
            CloudActionType actionType,
            String payloadSchemaVersion,
            String requestHash,
            LocalDateTime now
    ) {
        RagActionReceiptEntity entity = new RagActionReceiptEntity();
        entity.executionId = requireText(executionId, "executionId");
        entity.stepId = requireText(stepId, "stepId");
        entity.actorUserId = actorUserId;
        entity.actionType = actionType;
        entity.payloadSchemaVersion = requireText(payloadSchemaVersion, "payloadSchemaVersion");
        entity.requestHash = requireText(requestHash, "requestHash");
        entity.status = RagActionReceiptStatus.IN_PROGRESS;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void succeed(String resultCode, String resultJson, LocalDateTime completedAt) {
        this.status = RagActionReceiptStatus.SUCCEEDED;
        this.resultCode = requireText(resultCode, "resultCode");
        this.resultJson = requireText(resultJson, "resultJson");
        this.completedAt = completedAt;
        this.updatedAt = completedAt;
    }

    public String getStepId() {
        return stepId;
    }

    public String getExecutionId() {
        return executionId;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public CloudActionType getActionType() {
        return actionType;
    }

    public String getPayloadSchemaVersion() {
        return payloadSchemaVersion;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public RagActionReceiptStatus getStatus() {
        return status;
    }

    public String getResultCode() {
        return resultCode;
    }

    public String getResultJson() {
        return resultJson;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }
}
