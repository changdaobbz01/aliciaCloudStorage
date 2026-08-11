package com.alicia.cloudstorage.api.storage;

import java.util.List;
import java.util.Objects;

public record StorageNodeChangeEvent(
        StorageNodeChangeType changeType,
        List<StorageNodeReference> nodeReferences,
        boolean includeDescendants
) {

    public StorageNodeChangeEvent {
        Objects.requireNonNull(changeType, "changeType must not be null.");
        Objects.requireNonNull(nodeReferences, "nodeReferences must not be null.");
        nodeReferences = List.copyOf(nodeReferences);
    }

    public boolean isEmpty() {
        return nodeReferences.isEmpty();
    }
}
