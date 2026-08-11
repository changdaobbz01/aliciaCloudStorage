package com.alicia.cloudstorage.rag.assistant;

import java.util.List;

public record CandidateItem(
        Long nodeId,
        Long parentId,
        String name,
        String type,
        Long size,
        String extension,
        String mimeType,
        String updatedAt,
        String path,
        List<CandidateBreadcrumb> breadcrumbs
) {
    public CandidateItem(
            Long nodeId,
            Long parentId,
            String name,
            String type,
            Long size,
            String extension,
            String mimeType,
            String updatedAt
    ) {
        this(
                nodeId,
                parentId,
                name,
                type,
                size,
                extension,
                mimeType,
                updatedAt,
                "",
                List.of()
        );
    }

    public CandidateItem {
        name = name == null ? "" : name;
        type = type == null ? "" : type;
        extension = extension == null ? "" : extension;
        mimeType = mimeType == null ? "" : mimeType;
        updatedAt = updatedAt == null ? "" : updatedAt;
        path = path == null || path.isBlank() ? "/" + name : path;
        breadcrumbs = breadcrumbs == null ? List.of() : List.copyOf(breadcrumbs);
    }

    public CandidateItem withPath(String nextPath, List<CandidateBreadcrumb> nextBreadcrumbs) {
        return new CandidateItem(
                nodeId,
                parentId,
                name,
                type,
                size,
                extension,
                mimeType,
                updatedAt,
                nextPath,
                nextBreadcrumbs
        );
    }
}
