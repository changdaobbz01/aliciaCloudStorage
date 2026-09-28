package com.alicia.cloudstorage.ragexecution.domain.action;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;

import java.util.List;

public record NodeBatchMoveAction(
        List<BatchNodeSnapshot> items,
        Integer snapshotCount,
        String snapshotFingerprint,
        Long destinationParentId
) implements ExecutionActionPayload {
    public NodeBatchMoveAction {
        items = ActionPayloadRules.requireSnapshots(items);
        ActionPayloadRules.requireSnapshotCount(snapshotCount, items.size());
        snapshotFingerprint = ActionPayloadRules.requireSha256(snapshotFingerprint, "snapshotFingerprint");
        ActionPayloadRules.optionalPositive(destinationParentId, "destinationParentId");
    }

    @Override
    public ExecutionActionType actionType() {
        return ExecutionActionType.NODE_BATCH_MOVE;
    }
}
