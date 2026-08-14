package com.alicia.cloudstorage.api.dto;

import java.util.List;

public record ScopedTrashPreviewResponse(
        String selectorVersion,
        Long sourceParentId,
        boolean root,
        List<String> nodeTypes,
        List<StorageNodeSummaryResponse> items,
        int selectedFileCount,
        int selectedFolderCount,
        int descendantCount,
        int impactCount,
        String scopeFingerprint,
        String impactFingerprint,
        boolean executable,
        String message
) {
}
