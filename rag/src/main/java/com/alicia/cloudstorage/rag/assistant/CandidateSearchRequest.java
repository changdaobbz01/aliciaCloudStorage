package com.alicia.cloudstorage.rag.assistant;

public record CandidateSearchRequest(
        String intentId,
        String actionType,
        String candidateType,
        String queryRole,
        String query,
        int maxResults,
        String authorizationHeader
) {
    public CandidateSearchRequest(
            String intentId,
            String actionType,
            String candidateType,
            String query,
            int maxResults,
            String authorizationHeader
    ) {
        this(intentId, actionType, candidateType, "", query, maxResults, authorizationHeader);
    }

    public CandidateSearchRequest {
        intentId = intentId == null ? "" : intentId.trim();
        actionType = actionType == null ? "" : actionType.trim();
        candidateType = candidateType == null ? "" : candidateType.trim();
        queryRole = queryRole == null ? "" : queryRole.trim();
        query = query == null ? "" : query.trim();
        maxResults = Math.max(1, maxResults);
        authorizationHeader = authorizationHeader == null ? "" : authorizationHeader.trim();
    }
}
