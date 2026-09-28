package com.alicia.cloudstorage.api.ragexecution;

import com.alicia.cloudstorage.api.dto.StorageNodeSummaryResponse;
import com.alicia.cloudstorage.api.entity.NodeType;
import com.alicia.cloudstorage.api.entity.StorageNode;
import com.alicia.cloudstorage.api.ragexecution.api.CloudActionRequest;
import com.alicia.cloudstorage.api.ragexecution.application.CloudActionAuthorizationGuard;
import com.alicia.cloudstorage.api.ragexecution.application.CloudActionDispatchService;
import com.alicia.cloudstorage.api.ragexecution.application.CloudActionException;
import com.alicia.cloudstorage.api.ragexecution.application.CloudActionRequestHasher;
import com.alicia.cloudstorage.api.ragexecution.application.CloudActionRequestValidator;
import com.alicia.cloudstorage.api.ragexecution.application.CloudActionTransactionalExecutor;
import com.alicia.cloudstorage.api.ragexecution.application.CloudBatchSnapshotFingerprint;
import com.alicia.cloudstorage.api.ragexecution.application.CloudBatchSnapshotGuard;
import com.alicia.cloudstorage.api.ragexecution.config.RagExecutionCloudActionProperties;
import com.alicia.cloudstorage.api.ragexecution.persistence.RagActionReceiptRepository;
import com.alicia.cloudstorage.api.repository.StorageNodeRepository;
import com.alicia.cloudstorage.api.service.ShareLinkService;
import com.alicia.cloudstorage.api.service.StorageCommandService;
import com.alicia.cloudstorage.api.service.ScopedCollectionTrashService;
import com.alicia.cloudstorage.api.ragexecution.domain.BatchNodeSnapshotCloud;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false, properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Import({
        CloudActionDispatchService.class,
        CloudActionTransactionalExecutor.class,
        CloudActionAuthorizationGuard.class,
        CloudActionRequestValidator.class,
        CloudActionRequestHasher.class,
        CloudBatchSnapshotFingerprint.class,
        CloudBatchSnapshotGuard.class,
        CloudActionDispatchPersistenceTest.TestBeans.class
})
class CloudActionDispatchPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-09-18T04:00:00Z");

    @Autowired
    private CloudActionDispatchService dispatchService;

    @Autowired
    private CloudActionRequestHasher hasher;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private StorageNodeRepository storageNodeRepository;

    @Autowired
    private RagActionReceiptRepository receiptRepository;

    @Autowired
    private CloudBatchSnapshotFingerprint batchSnapshotFingerprint;

    @MockitoBean
    private StorageCommandService storageCommandService;

    @MockitoBean
    private ShareLinkService shareLinkService;

    @MockitoBean
    private ScopedCollectionTrashService scopedCollectionTrashService;

    private StorageNode ownedNode;

    @BeforeEach
    void setUp() {
        StorageNode node = new StorageNode();
        node.setOwnerId(42L);
        node.setNodeName("before.txt");
        node.setNodeType(NodeType.FILE);
        node.setFileSize(12L);
        ownedNode = storageNodeRepository.saveAndFlush(node);
        when(storageCommandService.renameNode(eq(42L), eq(ownedNode.getId()), any()))
                .thenReturn(new StorageNodeSummaryResponse(
                        ownedNode.getId(), null, "after.txt", "FILE", 12L,
                        "txt", "text/plain", LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), null
                ));
    }

    @Test
    void exactRetryReturnsStoredReceiptWithoutRepeatingBusinessMutation() {
        CloudActionRequest request = renameRequest(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                42L,
                "after.txt"
        );

        var first = dispatchService.dispatch(request);
        var retry = dispatchService.dispatch(request);

        assertThat(first.status()).isEqualTo("SUCCEEDED");
        assertThat(first.resultCode()).isEqualTo("NODE_RENAMED");
        assertThat(first.result().get("nodeId").longValue()).isEqualTo(ownedNode.getId());
        assertThat(retry.executionId()).isEqualTo(first.executionId());
        assertThat(retry.stepId()).isEqualTo(first.stepId());
        assertThat(retry.status()).isEqualTo(first.status());
        assertThat(retry.resultCode()).isEqualTo(first.resultCode());
        assertThat(retry.result().toString()).isEqualTo(first.result().toString());
        assertThat(retry.completedAt()).isEqualTo(first.completedAt());
        assertThat(receiptRepository.count()).isEqualTo(1);
        verify(storageCommandService).renameNode(eq(42L), eq(ownedNode.getId()), any());
    }

    @Test
    void sameStepWithChangedPayloadIsRejectedWithoutSecondMutation() {
        String executionId = UUID.randomUUID().toString();
        String stepId = UUID.randomUUID().toString();
        dispatchService.dispatch(renameRequest(executionId, stepId, 42L, "after.txt"));

        CloudActionRequest changed = renameRequest(executionId, stepId, 42L, "different.txt");

        assertThatThrownBy(() -> dispatchService.dispatch(changed))
                .isInstanceOfSatisfying(CloudActionException.class, exception -> {
                    assertThat(exception.statusCode()).isEqualTo(409);
                    assertThat(exception.errorCode()).isEqualTo("step_request_conflict");
                });
        verify(storageCommandService).renameNode(eq(42L), eq(ownedNode.getId()), any());
    }

    @Test
    void actorCannotOperateAnotherUsersNode() {
        CloudActionRequest request = renameRequest(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                99L,
                "stolen.txt"
        );

        assertThatThrownBy(() -> dispatchService.dispatch(request))
                .isInstanceOfSatisfying(CloudActionException.class, exception -> {
                    assertThat(exception.statusCode()).isEqualTo(403);
                    assertThat(exception.errorCode()).isEqualTo("actor_not_node_owner");
                });
        verify(storageCommandService, never()).renameNode(any(), any(), any());
    }

    @Test
    void staleEntityVersionIsRejectedBeforeBusinessMutation() {
        ObjectNode payload = jsonMapper.createObjectNode();
        payload.put("nodeId", ownedNode.getId());
        payload.put("expectedNodeVersion", ownedNode.getVersion() + 1);
        payload.put("newName", "after.txt");
        CloudActionRequest request = request(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), 42L, payload
        );

        assertThatThrownBy(() -> dispatchService.dispatch(request))
                .isInstanceOfSatisfying(CloudActionException.class, exception -> {
                    assertThat(exception.statusCode()).isEqualTo(409);
                    assertThat(exception.errorCode()).isEqualTo("node_version_conflict");
                });
        verify(storageCommandService, never()).renameNode(any(), any(), any());
    }

    @Test
    void batchSnapshotChangedAfterConfirmationIsRejectedBeforeMutation() {
        BatchNodeSnapshotCloud snapshot = snapshot(ownedNode);
        ObjectNode payload = jsonMapper.createObjectNode();
        ObjectNode item = payload.putArray("items").addObject();
        item.put("nodeId", snapshot.nodeId());
        item.putNull("parentId");
        item.put("name", snapshot.name());
        item.put("nodeType", snapshot.nodeType());
        item.put("updatedAt", snapshot.updatedAt());
        payload.put("snapshotCount", 1);
        payload.put("snapshotFingerprint", batchSnapshotFingerprint.hash(List.of(snapshot)));

        ownedNode.setNodeName("changed-after-confirmation.txt");
        storageNodeRepository.saveAndFlush(ownedNode);

        CloudActionRequest request = request(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), 42L,
                "NODE_BATCH_MOVE", payload
        );
        assertThatThrownBy(() -> dispatchService.dispatch(request))
                .isInstanceOfSatisfying(CloudActionException.class, exception -> {
                    assertThat(exception.statusCode()).isEqualTo(409);
                    assertThat(exception.errorCode()).isEqualTo("batch_snapshot_stale");
                });
        verify(storageCommandService, never()).moveNodes(any(), any());
    }

    @Test
    void currentBatchSnapshotExecutesOnceAndReturnsPerItemResults() {
        BatchNodeSnapshotCloud snapshot = snapshot(ownedNode);
        ObjectNode payload = jsonMapper.createObjectNode();
        ObjectNode item = payload.putArray("items").addObject();
        item.put("nodeId", snapshot.nodeId());
        item.putNull("parentId");
        item.put("name", snapshot.name());
        item.put("nodeType", snapshot.nodeType());
        item.put("updatedAt", snapshot.updatedAt());
        payload.put("snapshotCount", 1);
        payload.put("snapshotFingerprint", batchSnapshotFingerprint.hash(List.of(snapshot)));

        CloudActionRequest request = request(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), 42L,
                "NODE_BATCH_MOVE", payload
        );
        var first = dispatchService.dispatch(request);
        var retry = dispatchService.dispatch(request);

        assertThat(first.resultCode()).isEqualTo("NODES_MOVED");
        assertThat(first.result().path("requestedCount").asInt()).isEqualTo(1);
        assertThat(first.result().path("succeededCount").asInt()).isEqualTo(1);
        assertThat(first.result().path("failedCount").asInt()).isZero();
        assertThat(first.result().path("items").get(0).path("nodeId").asLong())
                .isEqualTo(ownedNode.getId());
        assertThat(retry.result().toString()).isEqualTo(first.result().toString());
        verify(storageCommandService).moveNodes(eq(42L), any());
    }

    @Test
    void uploadedNodesMustBelongToActorAndExpectedFolder() {
        StorageNode folder = node(42L, null, "target", NodeType.FOLDER);
        folder = storageNodeRepository.saveAndFlush(folder);
        StorageNode uploaded = node(42L, folder.getId(), "uploaded.txt", NodeType.FILE);
        uploaded = storageNodeRepository.saveAndFlush(uploaded);

        ObjectNode validPayload = jsonMapper.createObjectNode();
        validPayload.put("parentId", folder.getId());
        validPayload.putArray("nodeIds").add(uploaded.getId());
        var response = dispatchService.dispatch(request(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), 42L,
                "UPLOAD_FILES", validPayload
        ));
        assertThat(response.resultCode()).isEqualTo("UPLOADS_VERIFIED");
        assertThat(response.result().path("nodeIds").get(0).asLong()).isEqualTo(uploaded.getId());

        ObjectNode wrongParent = validPayload.deepCopy();
        wrongParent.put("parentId", folder.getId() + 999);
        assertThatThrownBy(() -> dispatchService.dispatch(request(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), 42L,
                "UPLOAD_FILES", wrongParent
        ))).isInstanceOfSatisfying(CloudActionException.class, exception -> {
            assertThat(exception.statusCode()).isEqualTo(409);
            assertThat(exception.errorCode()).isEqualTo("uploaded_nodes_not_verified");
        });
    }

    private StorageNode node(long ownerId, Long parentId, String name, NodeType type) {
        StorageNode node = new StorageNode();
        node.setOwnerId(ownerId);
        node.setParentId(parentId);
        node.setNodeName(name);
        node.setNodeType(type);
        node.setFileSize(0L);
        return node;
    }

    private CloudActionRequest renameRequest(
            String executionId,
            String stepId,
            long actorUserId,
            String newName
    ) {
        ObjectNode payload = jsonMapper.createObjectNode();
        payload.put("nodeId", ownedNode.getId());
        payload.put("expectedNodeVersion", ownedNode.getVersion());
        payload.put("newName", newName);
        return request(executionId, stepId, actorUserId, payload);
    }

    private CloudActionRequest request(
            String executionId,
            String stepId,
            long actorUserId,
            ObjectNode payload
    ) {
        return request(executionId, stepId, actorUserId, "NODE_RENAME", payload);
    }

    private CloudActionRequest request(
            String executionId,
            String stepId,
            long actorUserId,
            String actionType,
            ObjectNode payload
    ) {
        CloudActionRequest draft = new CloudActionRequest(
                executionId,
                stepId,
                actorUserId,
                actionType,
                CloudActionRequestValidator.PAYLOAD_SCHEMA_VERSION,
                payload,
                null,
                NOW
        );
        return new CloudActionRequest(
                draft.executionId(), draft.stepId(), draft.actorUserId(), draft.actionType(),
                draft.payloadSchemaVersion(), draft.payload(), hasher.hash(draft), draft.issuedAt()
        );
    }

    private BatchNodeSnapshotCloud snapshot(StorageNode node) {
        return new BatchNodeSnapshotCloud(
                node.getId(),
                node.getParentId(),
                node.getNodeName(),
                node.getNodeType().name(),
                node.getUpdatedAt().atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().findAndAddModules().build();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        RagExecutionCloudActionProperties ragExecutionCloudActionProperties() {
            return new RagExecutionCloudActionProperties(
                    true,
                    "cloud-action-test-secret-that-is-long-enough",
                    Duration.ofSeconds(60),
                    Duration.ofMinutes(3),
                    Duration.ofMinutes(2),
                    65_536
            );
        }
    }
}
