package com.alicia.cloudstorage.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ShareLinkDetailResponse(
        String shareCode,
        String title,
        String ownerNickname,
        LocalDateTime expiresAt,
        boolean allowDownload,
        boolean allowSave,
        List<Long> rootNodeIds,
        List<StorageNodeSummaryResponse> items
) {
}
