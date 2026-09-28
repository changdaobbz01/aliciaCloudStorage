package com.alicia.cloudstorage.ragexecution.port;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public interface CloudActionGateway {

    CloudActionResult dispatch(CloudActionCommand command);

    record CloudActionCommand(
            UUID executionId,
            UUID stepId,
            long actorUserId,
            ExecutionActionType actionType,
            String payloadSchemaVersion,
            JsonNode payload
    ) {
        public CloudActionCommand {
            if (executionId == null || stepId == null || actorUserId <= 0 || actionType == null
                    || payloadSchemaVersion == null || payloadSchemaVersion.isBlank() || payload == null) {
                throw new IllegalArgumentException("Cloud action command is incomplete.");
            }
            payload = payload.deepCopy();
        }
    }

    record CloudActionResult(
            String resultCode,
            JsonNode result,
            Instant completedAt
    ) {
        public CloudActionResult {
            if (resultCode == null || resultCode.isBlank() || result == null || completedAt == null) {
                throw new IllegalArgumentException("Cloud action result is incomplete.");
            }
            result = result.deepCopy();
        }
    }
}
