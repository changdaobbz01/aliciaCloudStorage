package com.alicia.cloudstorage.rag.assistant;

import java.util.List;

public record StorageApiNodePage(
        List<CandidateItem> items,
        long totalItems,
        int page,
        int size,
        int totalPages
) {
    public StorageApiNodePage {
        items = items == null ? List.of() : List.copyOf(items);
        totalItems = Math.max(0L, totalItems);
        page = Math.max(1, page);
        size = Math.max(1, size);
        totalPages = Math.max(0, totalPages);
    }
}
