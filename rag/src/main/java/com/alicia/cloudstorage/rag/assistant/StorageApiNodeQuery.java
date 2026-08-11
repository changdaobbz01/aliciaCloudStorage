package com.alicia.cloudstorage.rag.assistant;

public record StorageApiNodeQuery(
        Long parentId,
        boolean recursive,
        String keyword,
        String type,
        String category,
        int page,
        int size,
        String sortBy,
        String sortDirection
) {
    public StorageApiNodeQuery {
        keyword = keyword == null ? "" : keyword.trim();
        type = type == null ? "" : type.trim();
        category = category == null ? "" : category.trim();
        page = Math.max(1, page);
        size = Math.max(1, Math.min(100, size));
        sortBy = sortBy == null || sortBy.isBlank() ? "updatedAt" : sortBy.trim();
        sortDirection = sortDirection == null || sortDirection.isBlank() ? "desc" : sortDirection.trim();
    }
}
