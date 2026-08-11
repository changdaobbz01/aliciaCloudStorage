package com.alicia.cloudstorage.api.storage;

import com.alicia.cloudstorage.api.entity.StorageNode;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public interface StorageNodeEventPublisher {

    void publish(
            StorageNodeChangeType changeType,
            Collection<StorageNodeReference> references,
            boolean includeDescendants
    );

    default void publishUpsert(StorageNode node) {
        publishUpsert(List.of(node), false);
    }

    default void publishUpsert(Collection<StorageNode> nodes, boolean includeDescendants) {
        publish(StorageNodeChangeType.UPSERT, toReferences(nodes), includeDescendants);
    }

    default void publishRemove(Collection<StorageNode> nodes) {
        publish(StorageNodeChangeType.REMOVE, toReferences(nodes), false);
    }

    private List<StorageNodeReference> toReferences(Collection<StorageNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        return nodes.stream()
                .filter(Objects::nonNull)
                .filter(node -> node.getOwnerId() != null && node.getId() != null)
                .map(StorageNodeReference::from)
                .distinct()
                .toList();
    }
}
