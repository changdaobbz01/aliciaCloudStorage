package com.alicia.cloudstorage.api.dto;

import java.time.LocalDateTime;

public record AdminCloudShareLinkResponse(
        Long id,
        Long ownerId,
        String title,
        String status,
        String effectiveStatus,
        boolean passwordProtected,
        boolean allowDownload,
        boolean allowSave,
        long viewCount,
        long itemCount,
        LocalDateTime expiresAt,
        LocalDateTime lastAccessedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
