package com.alicia.cloudstorage.ragexecution.domain.action;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;

public record NodeTrashAction(
        long nodeId,
        Long expectedNodeVersion
) implements ExecutionActionPayload {

    public NodeTrashAction {
        ActionPayloadRules.requirePositive(nodeId, "nodeId");
        ActionPayloadRules.optionalNonNegative(expectedNodeVersion, "expectedNodeVersion");
    }

    @Override
    public ExecutionActionType actionType() {
        return ExecutionActionType.NODE_TRASH;
    }
}
