package com.alicia.cloudstorage.api.ragexecution.domain;

import java.util.List;

public record UploadFilesVerificationCloudAction(
        long parentId,
        List<Long> nodeIds
) implements CloudActionPayload {
    public UploadFilesVerificationCloudAction {
        parentId = CloudActionPayloadRules.positive(parentId, "parentId");
        if (nodeIds == null || nodeIds.isEmpty() || nodeIds.size() > CloudActionPayloadRules.HARD_MAX_BATCH_NODES) {
            throw new IllegalArgumentException("nodeIds must contain a supported number of files.");
        }
        nodeIds = List.copyOf(nodeIds);
        if (nodeIds.stream().anyMatch(value -> value == null || value <= 0)
                || nodeIds.stream().distinct().count() != nodeIds.size()) {
            throw new IllegalArgumentException("nodeIds must contain unique positive values.");
        }
    }

    @Override
    public CloudActionType actionType() {
        return CloudActionType.UPLOAD_FILES;
    }
}
