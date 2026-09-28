package com.alicia.cloudstorage.api.ragexecution.domain;

import java.util.List;

public record NodeBatchRenameCloudAction(
        List<BatchRenameItemCloud> items,
        int snapshotCount,
        String snapshotFingerprint
) implements CloudActionPayload {
    public NodeBatchRenameCloudAction {
        if (items == null || items.isEmpty() || items.size() > CloudActionPayloadRules.HARD_MAX_BATCH_NODES) {
            throw new IllegalArgumentException("items must contain a supported number of nodes.");
        }
        items = List.copyOf(items);
        if (items.stream().map(item -> item.snapshot().nodeId()).distinct().count() != items.size()) {
            throw new IllegalArgumentException("items must not contain duplicate nodeIds.");
        }
        CloudActionPayloadRules.snapshotCount(snapshotCount, items.size());
        snapshotFingerprint = CloudActionPayloadRules.sha256(snapshotFingerprint, "snapshotFingerprint");
    }

    @Override
    public CloudActionType actionType() {
        return CloudActionType.NODE_BATCH_RENAME;
    }
}
