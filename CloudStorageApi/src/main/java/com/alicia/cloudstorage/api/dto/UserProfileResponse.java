package com.alicia.cloudstorage.api.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record UserProfileResponse(
        Long id,
        String phoneNumber,
        String email,
        String nickname,
        String avatarUrl,
        String homeBackgroundUrl,
        String role,
        String status,
        LocalDateTime createdAt,
        Long storageQuotaBytes,
        long usedBytes,
        Long remainingBytes,
        Map<String, String> appRoles
) {

    public UserProfileResponse(
            Long id,
            String phoneNumber,
            String email,
            String nickname,
            String avatarUrl,
            String homeBackgroundUrl,
            String role,
            String status,
            LocalDateTime createdAt,
            Long storageQuotaBytes,
            long usedBytes,
            Long remainingBytes
    ) {
        this(
                id,
                phoneNumber,
                email,
                nickname,
                avatarUrl,
                homeBackgroundUrl,
                role,
                status,
                createdAt,
                storageQuotaBytes,
                usedBytes,
                remainingBytes,
                Map.of()
        );
    }

    public UserProfileResponse {
        appRoles = appRoles == null ? Map.of() : Map.copyOf(appRoles);
    }
}
