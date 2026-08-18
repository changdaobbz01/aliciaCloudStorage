package com.alicia.cloudstorage.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "cloud_user_profile")
public class CloudUserProfileEntity {

    @Id
    @Column(name = "identity_user_id")
    private Long identityUserId;

    @Column(name = "home_background_url", length = 500)
    private String homeBackgroundUrl;

    @Column(name = "storage_quota_bytes", nullable = false)
    private Long storageQuotaBytes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getIdentityUserId() {
        return identityUserId;
    }

    public void setIdentityUserId(Long identityUserId) {
        this.identityUserId = identityUserId;
    }

    public String getHomeBackgroundUrl() {
        return homeBackgroundUrl;
    }

    public void setHomeBackgroundUrl(String homeBackgroundUrl) {
        this.homeBackgroundUrl = homeBackgroundUrl;
    }

    public Long getStorageQuotaBytes() {
        return storageQuotaBytes;
    }

    public void setStorageQuotaBytes(Long storageQuotaBytes) {
        this.storageQuotaBytes = storageQuotaBytes;
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
