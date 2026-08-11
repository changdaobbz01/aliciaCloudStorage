package com.alicia.cloudstorage.api.storage;

import com.alicia.cloudstorage.api.entity.StorageNode;

import java.util.Objects;

public record StorageNodeReference(Long ownerId, Long nodeId) {

    public StorageNodeReference {
        Objects.requireNonNull(ownerId, "ownerId must not be null.");
        Objects.requireNonNull(nodeId, "nodeId must not be null.");
    }

    public static StorageNodeReference from(StorageNode node) {
        Objects.requireNonNull(node, "node must not be null.");
        return new StorageNodeReference(node.getOwnerId(), node.getId());
    }
}
