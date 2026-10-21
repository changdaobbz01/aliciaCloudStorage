package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.annotation.JsonInclude;

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
        @JsonInclude(JsonInclude.Include.NON_EMPTY) String method,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) String pathTemplate,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) String contentType,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> pathVariables,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> queryParameters,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> body,
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

    public BackendActionDraft forPublicClient() {
        boolean containedLegacyExecutionMaterial = executableByBackend
                || !blank(method)
                || !blank(pathTemplate)
                || !blank(path)
                || !blank(contentType)
                || !pathVariables.isEmpty()
                || !queryParameters.isEmpty()
                || !body.isEmpty();
        return new BackendActionDraft(
                status,
                bridgeVersion,
                actionType,
                containedLegacyExecutionMaterial ? "confirm_cloud_execution" : nextAction,
                false,
                false,
                authorizationRequired,
                "",
                "",
                "",
                "",
                Map.of(),
                Map.of(),
                Map.of(),
                requiredClientFields,
                targetCandidate,
                containedLegacyExecutionMaterial
                        ? "文件写操作只能通过云端任务确认执行。"
                        : message
        );
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
