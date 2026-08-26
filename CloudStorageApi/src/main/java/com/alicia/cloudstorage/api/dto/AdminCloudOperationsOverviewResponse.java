package com.alicia.cloudstorage.api.dto;

import java.time.LocalDateTime;

public record AdminCloudOperationsOverviewResponse(
        LocalDateTime generatedAt,
        CapacityOverview capacity,
        NodeOverview activeNodes,
        TrashOverview trash,
        ShareOverview shares,
        MultipartUploadOverview multipartUploads
) {

    public record CapacityOverview(
            long systemTotalSpaceBytes,
            long allocatedQuotaBytes,
            long actualUsedBytes,
            long remainingUnallocatedBytes,
            double allocatedUsageRatio,
            double actualUsageRatio
    ) {
    }

    public record NodeOverview(
            long totalItems,
            long folderCount,
            long fileCount
    ) {
    }

    public record TrashOverview(
            long totalItems,
            long rootItems,
            long folderCount,
            long fileCount,
            long bytes,
            LocalDateTime latestDeletedAt
    ) {
    }

    public record ShareOverview(
            long totalLinks,
            long activeLinks,
            long availableLinks,
            long expiredActiveLinks,
            long revokedLinks,
            long passwordProtectedLinks,
            long downloadEnabledLinks,
            long saveEnabledLinks,
            long totalViews,
            LocalDateTime latestCreatedAt,
            LocalDateTime latestAccessedAt
    ) {
    }

    public record MultipartUploadOverview(
            long totalSessions,
            long inProgressSessions,
            long staleInProgressSessions,
            long completedSessions,
            long abortedSessions,
            LocalDateTime latestInProgressUpdatedAt,
            long staleHours
    ) {
    }
}
