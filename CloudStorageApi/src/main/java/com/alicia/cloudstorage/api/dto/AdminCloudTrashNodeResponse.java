package com.alicia.cloudstorage.api.dto;

import java.time.LocalDateTime;

public record AdminCloudTrashNodeResponse(
        Long id,
        Long ownerId,
        Long parentId,
        Long originalParentId,
        String name,
        String type,
        long size,
        Long deletedBy,
        boolean rootItem,
        LocalDateTime deletedAt,
        LocalDateTime updatedAt
) {
}
