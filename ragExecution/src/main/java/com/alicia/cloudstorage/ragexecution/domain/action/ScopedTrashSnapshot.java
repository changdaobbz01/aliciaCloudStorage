package com.alicia.cloudstorage.ragexecution.domain.action;

import java.util.List;
import java.util.Locale;

public record ScopedTrashSnapshot(
        String selectorVersion,
        Long sourceParentId,
        boolean root,
        List<String> nodeTypes,
        String scopeFingerprint,
        String impactFingerprint,
        Integer expectedImpactCount
) {
    public ScopedTrashSnapshot {
        selectorVersion = ActionPayloadRules.requireText(selectorVersion, "selectorVersion", 64);
        ActionPayloadRules.optionalPositive(sourceParentId, "sourceParentId");
        if (root == (sourceParentId != null)) {
            throw new IllegalArgumentException("root and sourceParentId are inconsistent.");
        }
        if (nodeTypes == null || nodeTypes.isEmpty()) {
            throw new IllegalArgumentException("nodeTypes must not be empty.");
        }
        nodeTypes = nodeTypes.stream()
                .map(value -> ActionPayloadRules.requireNodeType(value).toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
        scopeFingerprint = ActionPayloadRules.requireSha256(scopeFingerprint, "scopeFingerprint");
        impactFingerprint = ActionPayloadRules.requireSha256(impactFingerprint, "impactFingerprint");
        if (expectedImpactCount == null || expectedImpactCount <= 0) {
            throw new IllegalArgumentException("expectedImpactCount must be positive.");
        }
    }
}
