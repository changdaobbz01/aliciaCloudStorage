package com.alicia.cloudstorage.api.ragexecution.api;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record CloudActionResponse(
        String executionId,
        String stepId,
        String status,
        String resultCode,
        JsonNode result,
        Instant completedAt
) {
}
