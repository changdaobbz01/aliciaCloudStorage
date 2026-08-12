package com.alicia.cloudstorage.rag.assistant;

public record CandidateSearchRequest(
        String intentId,
        String actionType,
        String candidateType,
        String queryRole,
        String query,
        String queryMode,
        String scope,
        String targetFolder,
        Long currentFolderId,
        String currentFolderPath,
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
        this(
                intentId,
                actionType,
                candidateType,
                "",
                query,
                "name_search",
                "all",
                "",
                null,
                "",
                maxResults,
                authorizationHeader
        );
    }

    public CandidateSearchRequest(
            String intentId,
            String actionType,
            String candidateType,
            String queryRole,
            String query,
            int maxResults,
            String authorizationHeader
    ) {
        this(
                intentId,
                actionType,
                candidateType,
                queryRole,
                query,
                "name_search",
                "all",
                "",
                null,
                "",
                maxResults,
                authorizationHeader
        );
    }

    public CandidateSearchRequest {
        intentId = intentId == null ? "" : intentId.trim();
        actionType = actionType == null ? "" : actionType.trim();
        candidateType = candidateType == null ? "" : candidateType.trim();
        queryRole = queryRole == null ? "" : queryRole.trim();
        query = query == null ? "" : query.trim();
        queryMode = queryMode == null || queryMode.isBlank() ? "name_search" : queryMode.trim();
        scope = scope == null || scope.isBlank() ? "all" : scope.trim();
        targetFolder = targetFolder == null ? "" : targetFolder.trim();
        currentFolderPath = currentFolderPath == null ? "" : currentFolderPath.trim();
        maxResults = Math.max(1, maxResults);
        authorizationHeader = authorizationHeader == null ? "" : authorizationHeader.trim();
    }
}
