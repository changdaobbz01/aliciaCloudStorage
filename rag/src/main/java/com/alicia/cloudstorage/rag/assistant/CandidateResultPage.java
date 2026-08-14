package com.alicia.cloudstorage.rag.assistant;

public record CandidateResultPage(
        Long totalCount,
        int returnedCount,
        Boolean hasMore,
        String sortBy,
        String sortDirection
) {
    public CandidateResultPage {
        returnedCount = Math.max(0, returnedCount);
        totalCount = totalCount == null ? null : Math.max(returnedCount, totalCount);
        sortBy = sortBy == null ? "" : sortBy.trim();
        sortDirection = sortDirection == null ? "" : sortDirection.trim().toLowerCase();
    }

    public static CandidateResultPage exact(
            long totalCount,
            int returnedCount,
            String sortBy,
            String sortDirection
    ) {
        int safeReturnedCount = Math.max(0, returnedCount);
        long safeTotalCount = Math.max(safeReturnedCount, totalCount);
        return new CandidateResultPage(
                safeTotalCount,
                safeReturnedCount,
                safeTotalCount > safeReturnedCount,
                sortBy,
                sortDirection
        );
    }

    public static CandidateResultPage unknown(
            int returnedCount,
            Boolean hasMore,
            String sortBy,
            String sortDirection
    ) {
        return new CandidateResultPage(null, returnedCount, hasMore, sortBy, sortDirection);
    }

    public CandidateResultPage withReturnedCount(int count) {
        int safeCount = Math.max(0, count);
        Boolean nextHasMore;
        if (totalCount == null) {
            nextHasMore = hasMore;
        } else {
            nextHasMore = totalCount > safeCount;
        }
        return new CandidateResultPage(totalCount, safeCount, nextHasMore, sortBy, sortDirection);
    }
}
