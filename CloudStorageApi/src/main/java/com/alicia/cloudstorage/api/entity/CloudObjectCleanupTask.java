package com.alicia.cloudstorage.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "cloud_object_cleanup_task")
public class CloudObjectCleanupTask {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 64)
    private CloudObjectCleanupSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CloudObjectCleanupStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CloudObjectCleanupTask() {
    }

    private CloudObjectCleanupTask(String objectKey, CloudObjectCleanupSource source, LocalDateTime now) {
        this.objectKey = requireText(objectKey, "objectKey");
        if (source == null) {
            throw new IllegalArgumentException("source is required.");
        }
        this.source = source;
        this.status = CloudObjectCleanupStatus.PENDING;
        this.attempts = 0;
        this.nextRetryAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static CloudObjectCleanupTask create(String objectKey, CloudObjectCleanupSource source, LocalDateTime now) {
        return new CloudObjectCleanupTask(objectKey, source, now);
    }

    public Long getId() {
        return id;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public CloudObjectCleanupSource getSource() {
        return source;
    }

    public CloudObjectCleanupStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void markCompleted(LocalDateTime completedAt) {
        this.status = CloudObjectCleanupStatus.COMPLETED;
        this.completedAt = completedAt;
        this.nextRetryAt = completedAt;
        this.lastError = null;
    }

    public void markFailed(String error, LocalDateTime nextRetryAt, int maxAttempts) {
        this.attempts += 1;
        this.lastError = truncate(error, MAX_ERROR_LENGTH);
        this.completedAt = null;
        this.nextRetryAt = nextRetryAt;
        this.status = attempts >= Math.max(1, maxAttempts)
                ? CloudObjectCleanupStatus.FAILED
                : CloudObjectCleanupStatus.RETRYING;
    }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (nextRetryAt == null) {
            nextRetryAt = now;
        }
        if (status == null) {
            status = CloudObjectCleanupStatus.PENDING;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
