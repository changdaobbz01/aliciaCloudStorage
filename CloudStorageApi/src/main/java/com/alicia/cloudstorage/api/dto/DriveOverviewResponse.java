package com.alicia.cloudstorage.api.dto;

public record DriveOverviewResponse(
        long totalItems,
        long totalFolders,
        long totalFiles,
        long usedBytes,
        Long totalSpaceBytes,
        long actualUsedBytes,
        String scope
) {
}
