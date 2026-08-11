package com.alicia.cloudstorage.rag.assistant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public record BackendActionDraft(
        String status,
        String bridgeVersion,
        String actionType,
        String nextAction,
        boolean confirmedByUser,
        boolean executableByBackend,
        boolean authorizationRequired,
        String method,
        String pathTemplate,
        String path,
        String contentType,
        Map<String, Object> pathVariables,
        Map<String, Object> queryParameters,
        Map<String, Object> body,
        List<String> requiredClientFields,
        CandidateItem targetCandidate,
        String message
) {
    public BackendActionDraft {
        pathVariables = immutableMap(pathVariables);
        queryParameters = immutableMap(queryParameters);
        body = immutableMap(body);
        requiredClientFields = requiredClientFields == null ? List.of() : List.copyOf(requiredClientFields);
    }

    public static BackendActionDraft skipped(String status, String message) {
        return new BackendActionDraft(
                status,
                "",
                "",
                "",
                false,
                false,
                false,
                "",
                "",
                "",
                "",
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                null,
                message
        );
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
