package com.alicia.cloudstorage.api.ragexecution.domain;

import java.util.List;

public record NodeBatchTrashCloudAction(
        List<BatchNodeSnapshotCloud> items,
        int snapshotCount,
        String snapshotFingerprint,
        ScopedTrashSnapshotCloud scopedTrash
) implements CloudActionPayload {
    public NodeBatchTrashCloudAction {
        items = CloudActionPayloadRules.snapshots(items);
        CloudActionPayloadRules.snapshotCount(snapshotCount, items.size());
        snapshotFingerprint = CloudActionPayloadRules.sha256(snapshotFingerprint, "snapshotFingerprint");
    }

    @Override
    public CloudActionType actionType() {
        return CloudActionType.NODE_BATCH_TRASH;
    }
}
