package com.alicia.cloudstorage.rag.assistant;

public record CandidateSearchRequest(
        String intentId,
        String actionType,
        String candidateType,
        String query,
        int maxResults,
        String authorizationHeader
) {
}
