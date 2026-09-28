package com.alicia.cloudstorage.ragexecution.domain.action;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;

import java.util.List;

public record NodeBatchTrashAction(
        List<BatchNodeSnapshot> items,
        Integer snapshotCount,
        String snapshotFingerprint,
        ScopedTrashSnapshot scopedTrash
) implements ExecutionActionPayload {
    public NodeBatchTrashAction {
        items = ActionPayloadRules.requireSnapshots(items);
        ActionPayloadRules.requireSnapshotCount(snapshotCount, items.size());
        snapshotFingerprint = ActionPayloadRules.requireSha256(snapshotFingerprint, "snapshotFingerprint");
    }

    @Override
    public ExecutionActionType actionType() {
        return ExecutionActionType.NODE_BATCH_TRASH;
    }
}
