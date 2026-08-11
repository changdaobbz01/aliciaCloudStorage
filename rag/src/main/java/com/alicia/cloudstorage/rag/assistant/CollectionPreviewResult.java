package com.alicia.cloudstorage.rag.assistant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CollectionPreviewResult(
        String status,
        String source,
        Map<String, Object> filter,
        List<CandidateItem> candidates,
        Integer totalCount,
        boolean exactCount,
        String message
) {
    public CollectionPreviewResult {
        status = status == null || status.isBlank() ? "unresolved" : status;
        source = source == null ? "" : source;
        filter = filter == null || filter.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(filter));
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        totalCount = totalCount == null ? candidates.size() : Math.max(0, totalCount);
        message = message == null ? "" : message;
    }

    public static CollectionPreviewResult skipped(String status, String message) {
        return new CollectionPreviewResult(status, "", Map.of(), List.of(), 0, false, message);
    }
}
