package com.alicia.cloudstorage.api.identity;

import java.time.LocalDateTime;

public record IdentityUserSnapshot(
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
