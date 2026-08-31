package com.alicia.cloudstorage.api.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record AdminCloudStorageUserUsageResponse(
        Long userId,
        String phoneNumber,
        String email,
        String nickname,
        String role,
        Map<String, String> appRoles,
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
    public AdminCloudStorageUserUsageResponse {
        appRoles = appRoles == null ? Map.of() : Map.copyOf(appRoles);
    }
}
