package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.identity.UserStatus;

import java.time.LocalDateTime;

record IdentityUserPayload(
        Long id,
        String phoneNumber,
        String email,
        String emailVerifiedAt,
        String nickname,
        String avatarUrl,
        Long tokenVersion,
        String role,
        String status,
        String createdAt
) {

    IdentityAccount toAccount() {
        return new IdentityAccount(
                id,
                phoneNumber,
                email,
                nickname,
                avatarUrl,
                UserRole.valueOf(role),
                UserStatus.valueOf(status),
                LocalDateTime.parse(createdAt)
        );
    }
}
