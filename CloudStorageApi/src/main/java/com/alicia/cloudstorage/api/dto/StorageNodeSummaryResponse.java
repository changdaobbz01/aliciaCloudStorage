package com.alicia.cloudstorage.api.dto;

import java.time.LocalDateTime;

public record StorageNodeSummaryResponse(
        Long id,
        Long parentId,
        String name,
        String type,
        Long size,
        String extension,
        String mimeType,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
