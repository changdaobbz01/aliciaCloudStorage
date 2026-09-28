package com.alicia.cloudstorage.api.ragexecution.domain;

public record BatchNodeSnapshotCloud(
        long nodeId,
        Long parentId,
        String name,
        String nodeType,
        String updatedAt
) {
    public BatchNodeSnapshotCloud {
        CloudActionPayloadRules.positive(nodeId, "nodeId");
        CloudActionPayloadRules.optionalPositive(parentId, "parentId");
        name = CloudActionPayloadRules.requiredText(name, "name", 255);
        nodeType = CloudActionPayloadRules.nodeType(nodeType);
        updatedAt = CloudActionPayloadRules.offsetTimestamp(updatedAt);
    }
}
