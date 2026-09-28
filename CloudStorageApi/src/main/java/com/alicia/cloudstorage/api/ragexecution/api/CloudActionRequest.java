package com.alicia.cloudstorage.api.ragexecution.api;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record CloudActionRequest(
        String executionId,
        String stepId,
        Long actorUserId,
        String actionType,
        String payloadSchemaVersion,
        JsonNode payload,
        String requestHash,
        Instant issuedAt
) {
}
