package com.alicia.cloudstorage.ragexecution.infrastructure.persistence;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionEventVisibility;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
        name = "rag_execution_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rag_execution_event_sequence",
                columnNames = {"execution_id", "sequence_no"}
        ),
        indexes = @Index(name = "idx_rag_execution_event_created", columnList = "execution_id,created_at")
)
public class ExecutionEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String executionId;

    @Column(name = "sequence_no", nullable = false)
    private long sequenceNo;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExecutionEventVisibility visibility;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "public_payload_json", nullable = false, columnDefinition = "json")
    private JsonNode publicPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExecutionEventJpaEntity() {
    }

    public static ExecutionEventJpaEntity create(
            UUID executionId,
            long sequenceNo,
            String eventType,
            ExecutionEventVisibility visibility,
            JsonNode publicPayload,
            Instant now
    ) {
        if (executionId == null || visibility == null || publicPayload == null || now == null) {
            throw new IllegalArgumentException("Execution event identity, visibility, payload, and time are required.");
        }
        if (sequenceNo <= 0) {
            throw new IllegalArgumentException("sequenceNo must be positive.");
        }
        if (eventType == null || eventType.isBlank() || eventType.length() > 64) {
            throw new IllegalArgumentException("eventType is invalid.");
        }
        ExecutionEventJpaEntity entity = new ExecutionEventJpaEntity();
        entity.executionId = executionId.toString();
        entity.sequenceNo = sequenceNo;
        entity.eventType = eventType.trim();
        entity.visibility = visibility;
        entity.publicPayload = publicPayload.deepCopy();
        entity.createdAt = now;
        return entity;
    }

    public Long getId() {
        return id;
    }

    public UUID getExecutionId() {
        return UUID.fromString(executionId);
    }

    public long getSequenceNo() {
        return sequenceNo;
    }

    public String getEventType() {
        return eventType;
    }

    public ExecutionEventVisibility getVisibility() {
        return visibility;
    }

    public JsonNode getPublicPayload() {
        return publicPayload.deepCopy();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
