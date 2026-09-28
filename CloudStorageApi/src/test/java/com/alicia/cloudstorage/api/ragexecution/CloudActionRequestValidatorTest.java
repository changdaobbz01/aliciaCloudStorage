package com.alicia.cloudstorage.api.ragexecution;

import com.alicia.cloudstorage.api.ragexecution.api.CloudActionRequest;
import com.alicia.cloudstorage.api.ragexecution.application.CloudActionException;
import com.alicia.cloudstorage.api.ragexecution.application.CloudActionRequestHasher;
import com.alicia.cloudstorage.api.ragexecution.application.CloudActionRequestValidator;
import com.alicia.cloudstorage.api.ragexecution.application.CloudBatchSnapshotFingerprint;
import com.alicia.cloudstorage.api.ragexecution.config.RagExecutionCloudActionProperties;
import com.alicia.cloudstorage.api.ragexecution.domain.NodeRenameCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.BatchNodeSnapshotCloud;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudActionRequestValidatorTest {

    private static final Instant NOW = Instant.parse("2026-09-18T04:00:00Z");

    private JsonMapper jsonMapper;
    private CloudActionRequestHasher hasher;
    private CloudActionRequestValidator validator;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().findAndAddModules().build();
        hasher = new CloudActionRequestHasher(jsonMapper);
        validator = new CloudActionRequestValidator(
                properties(),
                hasher,
                new CloudBatchSnapshotFingerprint(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void validatesTypedRenameAndAcceptsInitialJpaVersionZero() {
        ObjectNode payload = jsonMapper.createObjectNode();
        payload.put("nodeId", 17L);
        payload.put("expectedNodeVersion", 0L);
        payload.put("newName", "renamed.txt");

        var validated = validator.validate(signedRequest("NODE_RENAME", payload, NOW));

        assertThat(validated.payload()).isEqualTo(new NodeRenameCloudAction(17L, 0L, "renamed.txt"));
    }

    @Test
    void rejectsRequestHashMismatch() {
        ObjectNode payload = jsonMapper.createObjectNode();
        payload.put("nodeId", 17L);
        payload.put("newName", "renamed.txt");
        CloudActionRequest request = signedRequest("NODE_RENAME", payload, NOW);
        CloudActionRequest tampered = new CloudActionRequest(
                request.executionId(), request.stepId(), request.actorUserId(), request.actionType(),
                request.payloadSchemaVersion(), request.payload(), "0".repeat(64), request.issuedAt()
        );

        assertFailure(tampered, 422, "request_hash_mismatch");
    }

    @Test
    void rejectsArbitraryTransportFieldsAndUrls() {
        ObjectNode payload = jsonMapper.createObjectNode();
        payload.put("nodeId", 17L);
        payload.put("newName", "renamed.txt");
        payload.put("url", "https://attacker.example/action");

        assertFailure(signedRequest("NODE_RENAME", payload, NOW), 422, "prohibited_action_field");
    }

    @Test
    void rejectsStaleBusinessRequestEvenWhenItsHashIsValid() {
        ObjectNode payload = jsonMapper.createObjectNode();
        payload.put("nodeId", 17L);
        payload.put("newName", "renamed.txt");

        assertFailure(
                signedRequest("NODE_RENAME", payload, NOW.minus(Duration.ofMinutes(3))),
                409,
                "stale_action_request"
        );
    }

    @Test
    void rejectsSharePasswordsAtTheCloudActionBoundary() {
        ObjectNode payload = jsonMapper.createObjectNode();
        payload.putArray("nodeIds").add(17L);
        payload.put("password", "secret-code");

        assertFailure(signedRequest("SHARE_CREATE", payload, NOW), 422, "prohibited_action_field");
    }

    @Test
    void rejectsShareParametersOutsideCloudBusinessLimits() {
        ObjectNode payload = jsonMapper.createObjectNode();
        payload.putArray("nodeIds").add(17L);
        payload.put("expiresInDays", 366);

        assertFailure(signedRequest("SHARE_CREATE", payload, NOW), 422, "invalid_action_payload");
    }

    @Test
    void validatesBatchSnapshotAndRejectsCountOrFingerprintTampering() {
        ObjectNode valid = batchMovePayload();
        var validated = validator.validate(signedRequest("NODE_BATCH_MOVE", valid, NOW));
        assertThat(validated.actionType().name()).isEqualTo("NODE_BATCH_MOVE");

        ObjectNode wrongCount = valid.deepCopy();
        wrongCount.put("snapshotCount", 2);
        assertFailure(signedRequest("NODE_BATCH_MOVE", wrongCount, NOW), 422, "invalid_action_payload");

        ObjectNode wrongFingerprint = valid.deepCopy();
        wrongFingerprint.put("snapshotFingerprint", "0".repeat(64));
        assertFailure(
                signedRequest("NODE_BATCH_MOVE", wrongFingerprint, NOW),
                422,
                "batch_snapshot_fingerprint_mismatch"
        );
    }

    private ObjectNode batchMovePayload() {
        BatchNodeSnapshotCloud snapshot = new BatchNodeSnapshotCloud(
                17L, null, "source.txt", "FILE", "2026-09-18T12:00:00Z"
        );
        ObjectNode payload = jsonMapper.createObjectNode();
        ObjectNode item = payload.putArray("items").addObject();
        item.put("nodeId", snapshot.nodeId());
        item.putNull("parentId");
        item.put("name", snapshot.name());
        item.put("nodeType", snapshot.nodeType());
        item.put("updatedAt", snapshot.updatedAt());
        payload.put("snapshotCount", 1);
        payload.put("snapshotFingerprint", new CloudBatchSnapshotFingerprint().hash(List.of(snapshot)));
        payload.put("destinationParentId", 99L);
        return payload;
    }

    private CloudActionRequest signedRequest(String actionType, ObjectNode payload, Instant issuedAt) {
        CloudActionRequest draft = new CloudActionRequest(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                42L,
                actionType,
                CloudActionRequestValidator.PAYLOAD_SCHEMA_VERSION,
                payload,
                null,
                issuedAt
        );
        return new CloudActionRequest(
                draft.executionId(), draft.stepId(), draft.actorUserId(), draft.actionType(),
                draft.payloadSchemaVersion(), draft.payload(), hasher.hash(draft), draft.issuedAt()
        );
    }

    private void assertFailure(CloudActionRequest request, int status, String code) {
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOfSatisfying(CloudActionException.class, exception -> {
                    assertThat(exception.statusCode()).isEqualTo(status);
                    assertThat(exception.errorCode()).isEqualTo(code);
                });
    }

    private RagExecutionCloudActionProperties properties() {
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
