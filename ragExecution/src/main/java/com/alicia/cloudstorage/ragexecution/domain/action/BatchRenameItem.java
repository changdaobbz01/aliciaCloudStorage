package com.alicia.cloudstorage.ragexecution.domain.action;

public record BatchRenameItem(
        BatchNodeSnapshot snapshot,
        String newName
) {
    public BatchRenameItem {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot is required.");
        }
        newName = ActionPayloadRules.requireName(newName, "newName");
    }
}
