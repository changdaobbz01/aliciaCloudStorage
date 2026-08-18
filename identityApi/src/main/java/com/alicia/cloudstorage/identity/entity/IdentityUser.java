package com.alicia.cloudstorage.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Entity
@Immutable
@Table(name = "sys_user")
public class IdentityUser {

    @Id
    private Long id;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(length = 320)
    private String email;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "token_version", nullable = false)
    private Long tokenVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdentityUserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdentityUserStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected IdentityUser() {
    }

    public Long getId() {
        return id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public LocalDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Long getTokenVersion() {
        return tokenVersion;
    }

    public IdentityUserRole getRole() {
        return role;
    }

    public IdentityUserStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
