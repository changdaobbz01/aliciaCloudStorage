package com.alicia.cloudstorage.api.ragexecution.application;

import com.alicia.cloudstorage.api.dto.CreateFolderRequest;
import com.alicia.cloudstorage.api.dto.BatchMoveNodeRequest;
import com.alicia.cloudstorage.api.dto.BatchNodeRequest;
import com.alicia.cloudstorage.api.dto.BatchRenameNodeItem;
import com.alicia.cloudstorage.api.dto.BatchRenameNodeRequest;
import com.alicia.cloudstorage.api.dto.CreateShareLinkRequest;
import com.alicia.cloudstorage.api.dto.MoveNodeRequest;
import com.alicia.cloudstorage.api.dto.RenameNodeRequest;
import com.alicia.cloudstorage.api.dto.ShareLinkSummaryResponse;
import com.alicia.cloudstorage.api.dto.StorageNodeSummaryResponse;
import com.alicia.cloudstorage.api.dto.ScopedTrashRequest;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.ragexecution.api.CloudActionResponse;
import com.alicia.cloudstorage.api.ragexecution.application.CloudActionRequestValidator.ValidatedCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.CloudActionPayload;
import com.alicia.cloudstorage.api.ragexecution.domain.FolderCreateCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.NodeMoveCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.NodeBatchMoveCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.NodeBatchRenameCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.NodeBatchTrashCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.NodeRenameCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.NodeTrashCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.ShareCreateCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.UploadFilesVerificationCloudAction;
import com.alicia.cloudstorage.api.ragexecution.persistence.RagActionReceiptEntity;
import com.alicia.cloudstorage.api.ragexecution.persistence.RagActionReceiptRepository;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import com.alicia.cloudstorage.api.service.ShareLinkService;
import com.alicia.cloudstorage.api.service.ScopedCollectionTrashService;
import com.alicia.cloudstorage.api.service.StorageCommandService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ArrayNode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class CloudActionTransactionalExecutor {

    private final RagActionReceiptRepository receiptRepository;
    private final CloudActionAuthorizationGuard authorizationGuard;
    private final CloudBatchSnapshotGuard batchSnapshotGuard;
    private final StorageCommandService storageCommandService;
    private final ShareLinkService shareLinkService;
    private final ScopedCollectionTrashService scopedCollectionTrashService;
    private final StorageNodeRepository storageNodeRepository;
    private final EntityManager entityManager;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public CloudActionTransactionalExecutor(
            RagActionReceiptRepository receiptRepository,
            CloudActionAuthorizationGuard authorizationGuard,
            CloudBatchSnapshotGuard batchSnapshotGuard,
            StorageCommandService storageCommandService,
            ShareLinkService shareLinkService,
            ScopedCollectionTrashService scopedCollectionTrashService,
            StorageNodeRepository storageNodeRepository,
            EntityManager entityManager,
            JsonMapper jsonMapper,
            Clock clock
    ) {
        this.receiptRepository = receiptRepository;
        this.authorizationGuard = authorizationGuard;
        this.batchSnapshotGuard = batchSnapshotGuard;
        this.storageCommandService = storageCommandService;
        this.shareLinkService = shareLinkService;
        this.scopedCollectionTrashService = scopedCollectionTrashService;
        this.storageNodeRepository = storageNodeRepository;
        this.entityManager = entityManager;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public CloudActionResponse execute(ValidatedCloudAction action) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LocalDateTime localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        RagActionReceiptEntity receipt = RagActionReceiptEntity.begin(
                action.executionId(),
                action.stepId(),
                action.actorUserId(),
                action.actionType(),
                action.payloadSchemaVersion(),
                action.requestHash(),
                localNow
        );
        receiptRepository.saveAndFlush(receipt);

        ActionResult result;
        try {
            result = executeBusinessAction(action.actorUserId(), action.payload());
        } catch (CloudActionException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new CloudActionException(409, "business_rule_conflict", exception);
        }

        entityManager.flush();
        receipt.succeed(result.resultCode(), serialize(result.result()), localNow);
        receiptRepository.save(receipt);
        return new CloudActionResponse(
                action.executionId(),
                action.stepId(),
                "SUCCEEDED",
                result.resultCode(),
                result.result(),
                now
        );
    }

    private ActionResult executeBusinessAction(long actorUserId, CloudActionPayload payload) {
        return switch (payload) {
            case NodeRenameCloudAction action -> rename(actorUserId, action);
            case NodeTrashCloudAction action -> trash(actorUserId, action);
            case NodeMoveCloudAction action -> move(actorUserId, action);
            case FolderCreateCloudAction action -> createFolder(actorUserId, action);
            case ShareCreateCloudAction action -> createShare(actorUserId, action);
            case NodeBatchTrashCloudAction action -> batchTrash(actorUserId, action);
            case NodeBatchMoveCloudAction action -> batchMove(actorUserId, action);
            case NodeBatchRenameCloudAction action -> batchRename(actorUserId, action);
            case UploadFilesVerificationCloudAction action -> verifyUploads(actorUserId, action);
        };
    }

    private ActionResult verifyUploads(long actorUserId, UploadFilesVerificationCloudAction action) {
        List<StorageNode> nodes = storageNodeRepository.findByOwnerIdAndIdInAndDeletedFalse(
                actorUserId,
                action.nodeIds()
        );
        boolean valid = nodes.size() == action.nodeIds().size()
                && nodes.stream().allMatch(node -> node.getNodeType() == NodeType.FILE
                && java.util.Objects.equals(node.getParentId(), action.parentId()));
        if (!valid) {
            throw new CloudActionException(409, "uploaded_nodes_not_verified");
        }
        ObjectNode result = jsonMapper.createObjectNode();
        result.put("parentId", action.parentId());
        result.put("uploadedCount", action.nodeIds().size());
        ArrayNode ids = result.putArray("nodeIds");
        action.nodeIds().forEach(ids::add);
        return new ActionResult("UPLOADS_VERIFIED", result);
    }

    private ActionResult rename(long actorUserId, NodeRenameCloudAction action) {
        authorizationGuard.requireActiveNode(actorUserId, action.nodeId(), action.expectedNodeVersion());
        StorageNodeSummaryResponse response = storageCommandService.renameNode(
                actorUserId,
                action.nodeId(),
                new RenameNodeRequest(action.newName())
        );
        return nodeResult("NODE_RENAMED", response.id(), response.parentId(), response.type());
    }

    private ActionResult trash(long actorUserId, NodeTrashCloudAction action) {
        authorizationGuard.requireActiveNode(actorUserId, action.nodeId(), action.expectedNodeVersion());
        storageCommandService.moveNodeToTrash(actorUserId, action.nodeId());
        ObjectNode result = nodeIdentity(action.nodeId());
        result.put("trashed", true);
        return new ActionResult("NODE_TRASHED", result);
    }

    private ActionResult move(long actorUserId, NodeMoveCloudAction action) {
        authorizationGuard.requireActiveNode(actorUserId, action.nodeId(), action.expectedNodeVersion());
        if (action.destinationParentId() != null) {
            authorizationGuard.requireActiveFolder(actorUserId, action.destinationParentId());
        }
        StorageNodeSummaryResponse response = storageCommandService.moveNode(
                actorUserId,
                action.nodeId(),
                new MoveNodeRequest(action.destinationParentId())
        );
        return nodeResult("NODE_MOVED", response.id(), response.parentId(), response.type());
    }

    private ActionResult createFolder(long actorUserId, FolderCreateCloudAction action) {
        if (action.parentId() != null) {
            authorizationGuard.requireActiveFolder(actorUserId, action.parentId());
        }
        StorageNodeSummaryResponse response = storageCommandService.createFolder(
                actorUserId,
                new CreateFolderRequest(action.parentId(), action.folderName())
        );
        return nodeResult("FOLDER_CREATED", response.id(), response.parentId(), response.type());
    }

    private ActionResult createShare(long actorUserId, ShareCreateCloudAction action) {
        authorizationGuard.requireActiveNodes(actorUserId, action.nodeIds());
        ShareLinkSummaryResponse response = shareLinkService.createShareLink(
                actorUserId,
                new CreateShareLinkRequest(
                        action.nodeIds(),
                        action.title(),
                        null,
                        action.expiresInDays(),
                        action.allowDownload(),
                        action.allowSave()
                )
        );
        ObjectNode result = jsonMapper.createObjectNode();
        result.put("shareId", response.id());
        result.put("shareCode", response.shareCode());
        result.put("status", response.status());
        if (response.expiresAt() == null) {
            result.putNull("expiresAt");
        } else {
            result.put("expiresAt", response.expiresAt().toString());
        }
        return new ActionResult("SHARE_CREATED", result);
    }

    private ActionResult batchTrash(long actorUserId, NodeBatchTrashCloudAction action) {
        batchSnapshotGuard.requireCurrent(actorUserId, action.items(), action.snapshotFingerprint());
        List<Long> nodeIds = action.items().stream().map(item -> item.nodeId()).toList();
        if (action.scopedTrash() == null) {
            storageCommandService.moveNodesToTrash(actorUserId, new BatchNodeRequest(nodeIds));
        } else {
            var scope = action.scopedTrash();
            scopedCollectionTrashService.execute(actorUserId, new ScopedTrashRequest(
                    scope.selectorVersion(),
                    scope.sourceParentId(),
                    scope.root(),
                    scope.nodeTypes(),
                    nodeIds,
                    scope.scopeFingerprint(),
                    scope.impactFingerprint(),
                    scope.expectedImpactCount()
            ));
        }
        return batchResult("NODES_TRASHED", nodeIds, true);
    }

    private ActionResult batchMove(long actorUserId, NodeBatchMoveCloudAction action) {
        batchSnapshotGuard.requireCurrent(actorUserId, action.items(), action.snapshotFingerprint());
        if (action.destinationParentId() != null) {
            authorizationGuard.requireActiveFolder(actorUserId, action.destinationParentId());
        }
        List<Long> nodeIds = action.items().stream().map(item -> item.nodeId()).toList();
        storageCommandService.moveNodes(
                actorUserId,
                new BatchMoveNodeRequest(nodeIds, action.destinationParentId())
        );
        return batchResult("NODES_MOVED", nodeIds, false);
    }

    private ActionResult batchRename(long actorUserId, NodeBatchRenameCloudAction action) {
        batchSnapshotGuard.requireCurrent(
                actorUserId,
                action.items().stream().map(item -> item.snapshot()).toList(),
                action.snapshotFingerprint()
        );
        storageCommandService.renameNodes(actorUserId, new BatchRenameNodeRequest(
                action.items().stream()
                        .map(item -> new BatchRenameNodeItem(item.snapshot().nodeId(), item.newName()))
                        .toList()
        ));
        return batchResult(
                "NODES_RENAMED",
                action.items().stream().map(item -> item.snapshot().nodeId()).toList(),
                false
        );
    }

    private ActionResult batchResult(String resultCode, List<Long> nodeIds, boolean trashed) {
        entityManager.flush();
        ArrayNode items = jsonMapper.createArrayNode();
        for (Long nodeId : nodeIds) {
            StorageNode node = storageNodeRepository.findById(nodeId)
                    .orElseThrow(() -> new CloudActionException(500, "action_result_unavailable"));
            ObjectNode item = jsonMapper.createObjectNode();
            item.put("nodeId", nodeId);
            item.put("status", "SUCCEEDED");
            item.put("entityVersion", node.getVersion() == null ? 0L : node.getVersion());
            item.put("trashed", trashed);
            if (node.getParentId() == null) {
                item.putNull("parentId");
            } else {
                item.put("parentId", node.getParentId());
            }
            items.add(item);
        }
        ObjectNode result = jsonMapper.createObjectNode();
        result.put("requestedCount", nodeIds.size());
        result.put("succeededCount", nodeIds.size());
        result.put("failedCount", 0);
        result.set("items", items);
        return new ActionResult(resultCode, result);
    }

    private ActionResult nodeResult(String resultCode, Long nodeId, Long parentId, String nodeType) {
        ObjectNode result = nodeIdentity(nodeId);
        if (parentId == null) {
            result.putNull("parentId");
        } else {
            result.put("parentId", parentId);
        }
        result.put("nodeType", nodeType);
        return new ActionResult(resultCode, result);
    }

    private ObjectNode nodeIdentity(Long nodeId) {
        entityManager.flush();
        StorageNode node = storageNodeRepository.findById(nodeId)
                .orElseThrow(() -> new CloudActionException(500, "action_result_unavailable"));
        ObjectNode result = jsonMapper.createObjectNode();
        result.put("nodeId", nodeId);
        result.put("entityVersion", node.getVersion() == null ? 0L : node.getVersion());
        return result;
    }

    private String serialize(JsonNode value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new CloudActionException(500, "action_result_serialization_failed", exception);
        }
    }

    private record ActionResult(String resultCode, JsonNode result) {
    }
}
