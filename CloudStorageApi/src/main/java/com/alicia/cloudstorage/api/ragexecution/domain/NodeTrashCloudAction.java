package com.alicia.cloudstorage.api.ragexecution.domain;

public record NodeTrashCloudAction(
        long nodeId,
        Long expectedNodeVersion
) implements CloudActionPayload {

    public NodeTrashCloudAction {
        CloudActionPayloadRules.positive(nodeId, "nodeId");
        CloudActionPayloadRules.optionalNonNegative(expectedNodeVersion, "expectedNodeVersion");
    }

    @Override
    public CloudActionType actionType() {
        return CloudActionType.NODE_TRASH;
    }
}
