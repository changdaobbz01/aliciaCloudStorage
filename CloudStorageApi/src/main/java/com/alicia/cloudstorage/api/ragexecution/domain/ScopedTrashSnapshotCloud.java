package com.alicia.cloudstorage.api.ragexecution.domain;

import java.util.List;
import java.util.Locale;

public record ScopedTrashSnapshotCloud(
        String selectorVersion,
        Long sourceParentId,
        boolean root,
        List<String> nodeTypes,
        String scopeFingerprint,
        String impactFingerprint,
        int expectedImpactCount
) {
    public ScopedTrashSnapshotCloud {
        selectorVersion = CloudActionPayloadRules.requiredText(selectorVersion, "selectorVersion", 64);
        CloudActionPayloadRules.optionalPositive(sourceParentId, "sourceParentId");
        if (root == (sourceParentId != null)) {
            throw new IllegalArgumentException("root and sourceParentId are inconsistent.");
        }
        if (nodeTypes == null || nodeTypes.isEmpty()) {
            throw new IllegalArgumentException("nodeTypes must not be empty.");
        }
        nodeTypes = nodeTypes.stream()
                .map(CloudActionPayloadRules::nodeType)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
        scopeFingerprint = CloudActionPayloadRules.sha256(scopeFingerprint, "scopeFingerprint");
        impactFingerprint = CloudActionPayloadRules.sha256(impactFingerprint, "impactFingerprint");
        if (expectedImpactCount <= 0) {
            throw new IllegalArgumentException("expectedImpactCount must be positive.");
        }
    }
}
