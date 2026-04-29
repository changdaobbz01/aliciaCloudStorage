package com.alicia.cloudstorage.api.dto;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String phoneNumber,
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
}
