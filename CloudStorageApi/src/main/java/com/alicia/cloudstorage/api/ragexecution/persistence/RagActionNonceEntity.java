package com.alicia.cloudstorage.api.ragexecution.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "rag_execution_action_nonce",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rag_action_nonce_caller_nonce",
                columnNames = {"caller", "nonce"}
        )
)
public class RagActionNonceEntity {

    @Id
    @Column(name = "nonce_key", nullable = false, length = 140)
    private String nonceKey;

    @Column(nullable = false, length = 64)
    private String caller;

    @Column(nullable = false, length = 64)
    private String nonce;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected RagActionNonceEntity() {
    }

    public static RagActionNonceEntity create(
            String caller,
            String nonce,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
        RagActionNonceEntity entity = new RagActionNonceEntity();
        entity.caller = caller;
        entity.nonce = nonce;
        entity.nonceKey = caller + ":" + nonce;
        entity.expiresAt = expiresAt;
        entity.createdAt = createdAt;
        return entity;
    }
}
