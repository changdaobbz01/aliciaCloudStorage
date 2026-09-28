package com.alicia.cloudstorage.ragexecution.domain.action;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;

public record NodeMoveAction(
        long nodeId,
        Long expectedNodeVersion,
        Long destinationParentId
) implements ExecutionActionPayload {

    public NodeMoveAction {
        ActionPayloadRules.requirePositive(nodeId, "nodeId");
        ActionPayloadRules.optionalNonNegative(expectedNodeVersion, "expectedNodeVersion");
        ActionPayloadRules.optionalPositive(destinationParentId, "destinationParentId");
    }

    @Override
    public ExecutionActionType actionType() {
        return ExecutionActionType.NODE_MOVE;
    }
}
