package com.alicia.cloudstorage.ragexecution.domain.action;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;

public record NodeRenameAction(
        long nodeId,
        Long expectedNodeVersion,
        String newName
) implements ExecutionActionPayload {

    public NodeRenameAction {
        ActionPayloadRules.requirePositive(nodeId, "nodeId");
        ActionPayloadRules.optionalNonNegative(expectedNodeVersion, "expectedNodeVersion");
        newName = ActionPayloadRules.requireName(newName, "newName");
    }

    @Override
    public ExecutionActionType actionType() {
        return ExecutionActionType.NODE_RENAME;
    }
}
