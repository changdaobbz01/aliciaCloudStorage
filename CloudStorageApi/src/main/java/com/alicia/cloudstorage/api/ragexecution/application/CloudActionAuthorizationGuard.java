package com.alicia.cloudstorage.api.ragexecution.application;

import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CloudActionAuthorizationGuard {

    private final StorageNodeRepository storageNodeRepository;

    public CloudActionAuthorizationGuard(StorageNodeRepository storageNodeRepository) {
        this.storageNodeRepository = storageNodeRepository;
    }

    public StorageNode requireActiveNode(long actorUserId, long nodeId, Long expectedVersion) {
        StorageNode node = storageNodeRepository.findById(nodeId)
                .orElseThrow(() -> new CloudActionException(404, "node_not_found"));
        if (!Long.valueOf(actorUserId).equals(node.getOwnerId())) {
            throw new CloudActionException(403, "actor_not_node_owner");
        }
        if (node.isDeleted()) {
            throw new CloudActionException(409, "node_not_active");
        }
        long actualVersion = node.getVersion() == null ? 0L : node.getVersion();
        if (expectedVersion != null && expectedVersion != actualVersion) {
            throw new CloudActionException(409, "node_version_conflict");
        }
        return node;
    }

    public StorageNode requireActiveFolder(long actorUserId, long folderId) {
        StorageNode folder = requireActiveNode(actorUserId, folderId, null);
        if (folder.getNodeType() != NodeType.FOLDER) {
            throw new CloudActionException(409, "destination_not_folder");
        }
        return folder;
    }

    public List<StorageNode> requireActiveNodes(long actorUserId, List<Long> nodeIds) {
        return nodeIds.stream()
                .map(nodeId -> requireActiveNode(actorUserId, nodeId, null))
                .toList();
    }
}
