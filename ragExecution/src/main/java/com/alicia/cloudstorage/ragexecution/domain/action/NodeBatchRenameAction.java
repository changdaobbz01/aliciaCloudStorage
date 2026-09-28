package com.alicia.cloudstorage.ragexecution.domain.action;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;

import java.util.List;

public record NodeBatchRenameAction(
        List<BatchRenameItem> items,
        Integer snapshotCount,
        String snapshotFingerprint
) implements ExecutionActionPayload {
    public NodeBatchRenameAction {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty.");
        }
        items = List.copyOf(items);
        if (items.size() > ActionPayloadRules.HARD_MAX_BATCH_NODES
                || items.stream().map(item -> item.snapshot().nodeId()).distinct().count() != items.size()) {
            throw new IllegalArgumentException("items contain too many nodes or duplicate nodeIds.");
        }
        ActionPayloadRules.requireSnapshotCount(snapshotCount, items.size());
        snapshotFingerprint = ActionPayloadRules.requireSha256(snapshotFingerprint, "snapshotFingerprint");
    }

    @Override
    public ExecutionActionType actionType() {
        return ExecutionActionType.NODE_BATCH_RENAME;
    }
}
