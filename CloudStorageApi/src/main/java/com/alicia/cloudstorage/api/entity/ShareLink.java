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
@Table(name = "share_link")
public class ShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "share_code", nullable = false, unique = true)
    private String shareCode;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "allow_download", nullable = false)
    private boolean allowDownload;

    @Column(name = "allow_save", nullable = false)
    private boolean allowSave;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShareLinkStatus status;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getShareCode() {
        return shareCode;
    }

    public void setShareCode(String shareCode) {
        this.shareCode = shareCode;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isAllowDownload() {
        return allowDownload;
    }

    public void setAllowDownload(boolean allowDownload) {
        this.allowDownload = allowDownload;
    }

    public boolean isAllowSave() {
        return allowSave;
    }

    public void setAllowSave(boolean allowSave) {
        this.allowSave = allowSave;
    }

    public ShareLinkStatus getStatus() {
        return status;
    }

    public void setStatus(ShareLinkStatus status) {
        this.status = status;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
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

        if (status == null) {
            status = ShareLinkStatus.ACTIVE;
        }

        if (viewCount == null) {
            viewCount = 0L;
        }

        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
