package com.alicia.cloudstorage.ragexecution.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "rag_execution_service_nonce",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rag_execution_service_nonce",
                columnNames = {"caller_service", "nonce"}
        ),
        indexes = @Index(name = "idx_rag_execution_service_nonce_expiry", columnList = "expires_at")
)
public class ServiceNonceJpaEntity {

    @Id
    @Column(nullable = false, length = 128)
    private String id;

    @Column(name = "caller_service", nullable = false, length = 32)
    private String callerService;

    @Column(nullable = false, length = 64)
    private String nonce;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ServiceNonceJpaEntity() {
    }

    public static ServiceNonceJpaEntity create(
            String callerService,
            String nonce,
            Instant expiresAt,
            Instant now
    ) {
        ServiceNonceJpaEntity entity = new ServiceNonceJpaEntity();
        entity.callerService = requireText(callerService, 32, "callerService");
        entity.nonce = requireText(nonce, 64, "nonce");
        entity.id = entity.callerService + ":" + entity.nonce;
        entity.expiresAt = requireInstant(expiresAt, "expiresAt");
        entity.createdAt = requireInstant(now, "now");
        if (!entity.expiresAt.isAfter(entity.createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after now.");
        }
        return entity;
    }

    private static String requireText(String value, int maximum, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > maximum || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(field + " has an invalid format.");
        }
        return normalized;
    }

    private static Instant requireInstant(Instant value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }
}
