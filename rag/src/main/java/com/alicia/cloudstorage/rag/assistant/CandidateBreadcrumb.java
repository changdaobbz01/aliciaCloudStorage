package com.alicia.cloudstorage.rag.assistant;

public record CandidateBreadcrumb(
        Long nodeId,
        String name
) {
    public CandidateBreadcrumb {
        name = name == null ? "" : name;
    }
}
