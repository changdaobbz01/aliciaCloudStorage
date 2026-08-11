package com.alicia.cloudstorage.rag.assistant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ActionPlanStep(
        String stepId,
        String action,
        String status,
        Map<String, Object> params,
        List<String> dependsOn,
        List<String> requiredClientFields,
        String outputKey
) {
    public ActionPlanStep {
        stepId = stepId == null ? "" : stepId;
        action = action == null ? "" : action;
        status = status == null ? "pending" : status;
        params = params == null || params.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params));
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        requiredClientFields = requiredClientFields == null ? List.of() : List.copyOf(requiredClientFields);
        outputKey = outputKey == null ? "" : outputKey;
    }
}
