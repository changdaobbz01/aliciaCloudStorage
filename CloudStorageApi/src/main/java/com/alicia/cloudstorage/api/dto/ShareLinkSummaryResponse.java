package com.alicia.cloudstorage.api.dto;

import java.time.LocalDateTime;

public record ShareLinkSummaryResponse(
        Long id,
        String shareCode,
        String title,
        boolean hasPassword,
        LocalDateTime expiresAt,
        boolean allowDownload,
        boolean allowSave,
        String status,
        Long viewCount,
        LocalDateTime lastAccessedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long itemCount
) {
}
