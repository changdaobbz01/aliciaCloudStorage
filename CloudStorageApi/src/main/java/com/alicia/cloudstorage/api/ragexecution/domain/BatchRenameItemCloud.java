package com.alicia.cloudstorage.api.ragexecution.domain;

public record BatchRenameItemCloud(
        BatchNodeSnapshotCloud snapshot,
        String newName
) {
    public BatchRenameItemCloud {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot is required.");
        }
        newName = CloudActionPayloadRules.requiredName(newName, "newName");
    }
}
