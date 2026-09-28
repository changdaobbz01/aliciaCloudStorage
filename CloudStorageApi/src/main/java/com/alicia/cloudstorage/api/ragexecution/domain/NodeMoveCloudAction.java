package com.alicia.cloudstorage.api.ragexecution.domain;

public record NodeMoveCloudAction(
        long nodeId,
        Long expectedNodeVersion,
        Long destinationParentId
) implements CloudActionPayload {

    public NodeMoveCloudAction {
        CloudActionPayloadRules.positive(nodeId, "nodeId");
        CloudActionPayloadRules.optionalNonNegative(expectedNodeVersion, "expectedNodeVersion");
        CloudActionPayloadRules.optionalPositive(destinationParentId, "destinationParentId");
    }

    @Override
    public CloudActionType actionType() {
        return CloudActionType.NODE_MOVE;
    }
}
