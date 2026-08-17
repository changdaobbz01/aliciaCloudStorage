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
@Table(name = "email_verification_code")
public class EmailVerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EmailVerificationPurpose purpose;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "resend_after", nullable = false)
    private LocalDateTime resendAfter;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "request_ip_hash", length = 128)
    private String requestIpHash;

    @Column(name = "user_agent_hash", length = 128)
    private String userAgentHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public EmailVerificationPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(EmailVerificationPurpose purpose) {
        this.purpose = purpose;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getResendAfter() {
        return resendAfter;
    }

    public void setResendAfter(LocalDateTime resendAfter) {
        this.resendAfter = resendAfter;
    }

    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }

    public String getRequestIpHash() {
        return requestIpHash;
    }

    public void setRequestIpHash(String requestIpHash) {
        this.requestIpHash = requestIpHash;
    }

    public String getUserAgentHash() {
        return userAgentHash;
    }

    public void setUserAgentHash(String userAgentHash) {
        this.userAgentHash = userAgentHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
