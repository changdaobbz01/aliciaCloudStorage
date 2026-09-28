package com.alicia.cloudstorage.api.ragexecution.domain;

import java.util.List;

public record NodeBatchMoveCloudAction(
        List<BatchNodeSnapshotCloud> items,
        int snapshotCount,
        String snapshotFingerprint,
        Long destinationParentId
) implements CloudActionPayload {
    public NodeBatchMoveCloudAction {
        items = CloudActionPayloadRules.snapshots(items);
        CloudActionPayloadRules.snapshotCount(snapshotCount, items.size());
        snapshotFingerprint = CloudActionPayloadRules.sha256(snapshotFingerprint, "snapshotFingerprint");
        CloudActionPayloadRules.optionalPositive(destinationParentId, "destinationParentId");
    }

    @Override
    public CloudActionType actionType() {
        return CloudActionType.NODE_BATCH_MOVE;
    }
}
