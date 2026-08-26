package com.alicia.cloudstorage.api.dto;

import java.time.LocalDateTime;

public record AdminCloudStorageUserUsageResponse(
        Long userId,
        String phoneNumber,
        String email,
        String nickname,
        String role,
        String status,
        Long storageQuotaBytes,
        long usedBytes,
        Long remainingBytes,
        Double usageRatio,
        long activeItems,
        long activeFolders,
        long activeFiles,
        long trashItems,
        long shareLinks,
        LocalDateTime createdAt
) {
}
