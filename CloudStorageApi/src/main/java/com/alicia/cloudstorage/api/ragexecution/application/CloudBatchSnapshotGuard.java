package com.alicia.cloudstorage.api.ragexecution.application;

import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.ragexecution.domain.BatchNodeSnapshotCloud;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class CloudBatchSnapshotGuard {

    private final StorageNodeRepository storageNodeRepository;
    private final CloudBatchSnapshotFingerprint fingerprint;

    public CloudBatchSnapshotGuard(
            StorageNodeRepository storageNodeRepository,
            CloudBatchSnapshotFingerprint fingerprint
    ) {
        this.storageNodeRepository = storageNodeRepository;
        this.fingerprint = fingerprint;
    }

    public List<StorageNode> requireCurrent(
            long actorUserId,
            List<BatchNodeSnapshotCloud> expected,
            String expectedFingerprint
    ) {
        List<Long> nodeIds = expected.stream().map(BatchNodeSnapshotCloud::nodeId).toList();
        List<StorageNode> nodes = storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(actorUserId, nodeIds);
        if (nodes.size() != expected.size()) {
            throw new CloudActionException(409, "batch_snapshot_stale");
        }
        List<BatchNodeSnapshotCloud> current = nodes.stream().map(this::snapshot).toList();
        if (!fingerprint.hash(current).equals(expectedFingerprint)) {
            throw new CloudActionException(409, "batch_snapshot_stale");
        }
        return nodes;
    }

    private BatchNodeSnapshotCloud snapshot(StorageNode node) {
        String updatedAt = node.getUpdatedAt().atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new BatchNodeSnapshotCloud(
                node.getId(),
                node.getParentId(),
                node.getNodeName(),
                node.getNodeType().name(),
                updatedAt
        );
    }
}
