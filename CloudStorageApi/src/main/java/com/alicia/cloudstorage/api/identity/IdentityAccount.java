package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.identity.UserStatus;

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
