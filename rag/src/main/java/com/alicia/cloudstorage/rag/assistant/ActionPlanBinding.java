package com.alicia.cloudstorage.rag.assistant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ActionPlanBinding(
        String key,
        String kind,
        String status,
        String query,
        CandidateItem selectedCandidate,
        List<CandidateItem> candidates,
        Integer count,
        Map<String, Object> filter
) {
    public ActionPlanBinding {
        key = key == null ? "" : key;
        kind = kind == null ? "" : kind;
        status = status == null ? "" : status;
        query = query == null ? "" : query;
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        filter = filter == null || filter.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(filter));
    }
}
