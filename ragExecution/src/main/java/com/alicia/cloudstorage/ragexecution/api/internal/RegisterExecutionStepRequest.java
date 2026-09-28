package com.alicia.cloudstorage.ragexecution.api.internal;

public record RegisterExecutionStepRequest(
        String stepKey,
        String actionType,
        String payloadSchemaVersion,
        Object payload,
        java.util.List<String> dependsOn,
        String outputKey,
        java.util.List<String> requiredClientFields
) {
    public RegisterExecutionStepRequest(String actionType, String payloadSchemaVersion, Object payload) {
        this("step", actionType, payloadSchemaVersion, payload, java.util.List.of(), "", java.util.List.of());
    }
}
