package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;

import java.time.LocalDateTime;

public record IdentityAccount(
        Long id,
        String phoneNumber,
        String email,
        String nickname,
        String avatarUrl,
        UserRole role,
        UserStatus status,
        LocalDateTime createdAt
) {

    public String phoneNumberOrEmpty() {
        return phoneNumber == null ? "" : phoneNumber;
    }
}
