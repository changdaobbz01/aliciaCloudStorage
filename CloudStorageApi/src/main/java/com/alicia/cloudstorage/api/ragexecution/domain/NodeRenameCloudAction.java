package com.alicia.cloudstorage.api.ragexecution.domain;

public record NodeRenameCloudAction(
        long nodeId,
        Long expectedNodeVersion,
        String newName
) implements CloudActionPayload {

    public NodeRenameCloudAction {
        CloudActionPayloadRules.positive(nodeId, "nodeId");
        CloudActionPayloadRules.optionalNonNegative(expectedNodeVersion, "expectedNodeVersion");
        newName = CloudActionPayloadRules.requiredName(newName, "newName");
    }

    @Override
    public CloudActionType actionType() {
        return CloudActionType.NODE_RENAME;
    }
}
