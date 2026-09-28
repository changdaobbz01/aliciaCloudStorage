package com.alicia.cloudstorage.ragexecution.domain.action;

public record BatchNodeSnapshot(
        Long nodeId,
        Long parentId,
        String name,
        String nodeType,
        String updatedAt
) {
    public BatchNodeSnapshot {
        ActionPayloadRules.requirePositive(nodeId, "nodeId");
        ActionPayloadRules.optionalPositive(parentId, "parentId");
        name = ActionPayloadRules.requireName(name, "name");
        nodeType = ActionPayloadRules.requireNodeType(nodeType);
        updatedAt = ActionPayloadRules.requireTimestamp(updatedAt);
    }
}
