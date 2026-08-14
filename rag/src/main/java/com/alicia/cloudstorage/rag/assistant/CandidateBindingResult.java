package com.alicia.cloudstorage.rag.assistant;

import java.util.List;

public record CandidateBindingResult(
        String status,
        String source,
        String query,
        String candidateType,
        List<CandidateItem> candidates,
        String message,
        CandidateItem selectedCandidate,
        Integer selectedIndex,
        CandidateResultPage pageInfo
) {
    public CandidateBindingResult(
            String status,
            String source,
            String query,
            String candidateType,
            List<CandidateItem> candidates,
            String message,
            CandidateItem selectedCandidate,
            Integer selectedIndex
    ) {
        this(
                status,
                source,
                query,
                candidateType,
                candidates,
                message,
                selectedCandidate,
                selectedIndex,
                null
        );
    }

    public CandidateBindingResult(
            String status,
            String source,
            String query,
            String candidateType,
            List<CandidateItem> candidates,
            String message
    ) {
        this(status, source, query, candidateType, candidates, message, null, null, null);
    }

    public CandidateBindingResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        pageInfo = pageInfo == null
                ? CandidateResultPage.unknown(candidates.size(), null, "", "")
                : pageInfo.withReturnedCount(candidates.size());
    }

    public static CandidateBindingResult skipped(String status, String message) {
        return new CandidateBindingResult(status, "", "", "", List.of(), message);
    }

    public CandidateBindingResult select(int zeroBasedIndex) {
        if (zeroBasedIndex < 0 || zeroBasedIndex >= candidates.size()) {
            return new CandidateBindingResult(
                    "candidate_selection_out_of_range",
                    source,
                    query,
                    candidateType,
                    candidates,
                    "选择序号超出候选范围，请在 1-" + candidates.size() + " 之间选择。",
                    null,
                    null,
                    pageInfo
            );
        }

        CandidateItem selected = candidates.get(zeroBasedIndex);
        int oneBasedIndex = zeroBasedIndex + 1;
        return new CandidateBindingResult(
                "selected_candidate",
                source,
                query,
                candidateType,
                candidates,
                "已选择第 " + oneBasedIndex + " 个候选：" + selected.name() + "。等待用户确认。",
                selected,
                oneBasedIndex,
                pageInfo
        );
    }
}
