package com.alicia.cloudstorage.rag.execution;

import com.fasterxml.jackson.databind.JsonNode;

public record ExecutionRegistrationStep(
        String stepKey,
        String actionType,
        String payloadSchemaVersion,
        JsonNode payload,
        java.util.List<String> dependsOn,
        String outputKey,
        java.util.List<String> requiredClientFields
) {
    public ExecutionRegistrationStep(String actionType, String payloadSchemaVersion, JsonNode payload) {
        this("step", actionType, payloadSchemaVersion, payload, java.util.List.of(), "", java.util.List.of());
    }
}
