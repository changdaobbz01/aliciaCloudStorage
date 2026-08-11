package com.alicia.cloudstorage.rag.assistant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CollectionPreviewRequest(
        String actionType,
        Map<String, Object> filter,
        int maxPreviewItems,
        int maxScanItems,
        String authorizationHeader
) {
    public CollectionPreviewRequest {
        actionType = actionType == null ? "" : actionType.trim();
        filter = filter == null || filter.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(filter));
        maxPreviewItems = Math.max(1, maxPreviewItems);
        maxScanItems = Math.max(maxPreviewItems, maxScanItems);
        authorizationHeader = authorizationHeader == null ? "" : authorizationHeader.trim();
    }
}
