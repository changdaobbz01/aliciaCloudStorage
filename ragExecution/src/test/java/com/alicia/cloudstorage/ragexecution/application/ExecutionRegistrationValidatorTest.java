package com.alicia.cloudstorage.ragexecution.application;

import com.alicia.cloudstorage.ragexecution.api.internal.RegisterExecutionRequest;
import com.alicia.cloudstorage.ragexecution.api.internal.RegisterExecutionStepRequest;
import com.alicia.cloudstorage.ragexecution.config.RagExecutionLimitsProperties;
import com.alicia.cloudstorage.ragexecution.domain.action.BatchNodeSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionRegistrationValidatorTest {

    private static final Instant NOW = Instant.parse("2026-09-18T08:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final CanonicalJsonHasher hasher = new CanonicalJsonHasher(objectMapper);
    private final ExecutionRegistrationValidator validator = new ExecutionRegistrationValidator(
            objectMapper,
            limits(65_536),
            () -> NOW,
            hasher,
            new BatchSnapshotFingerprint()
    );

    @Test
    void validatesTypedPayloadAndMatchingPlanHash() {
        RegisterExecutionRequest request = renameRequest(renamePayload(), "NODE_RENAME", NOW.plusSeconds(300));

        ExecutionRegistrationValidator.ValidatedRegistration validated = validator.validate(request);

        assertThat(validated.steps()).hasSize(1);
        assertThat(validated.steps().getFirst().actionType().name()).isEqualTo("NODE_RENAME");
    }

    @Test
    void rejectsUnknownActionsUrlsAndHashMismatch() {
        RegisterExecutionRequest unknownAction = renameRequest(
                renamePayload(), "DOWNLOAD_TO_DEVICE", NOW.plusSeconds(300)
        );
        ObjectNode urlPayload = renamePayload();
        urlPayload.put("url", "https://internal.example/write");
        RegisterExecutionRequest arbitraryUrl = renameRequest(urlPayload, "NODE_RENAME", NOW.plusSeconds(300));
        RegisterExecutionRequest wrongHash = withHash(
                renameRequest(renamePayload(), "NODE_RENAME", NOW.plusSeconds(300)),
                "0".repeat(64)
        );

        assertError(unknownAction, "unsupported_action_type");
        assertError(arbitraryUrl, "prohibited_action_field");
        assertError(wrongHash, "plan_hash_mismatch");
    }

    @Test
    void rejectsSharePasswordsBeforeTheyCanBePersistedInExecutionPayloads() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putArray("nodeIds").add(17L);
        payload.put("password", "secret-code");

        assertError(
                renameRequest(payload, "SHARE_CREATE", NOW.plusSeconds(300)),
                "prohibited_action_field"
        );
    }

    @Test
    void rejectsExpiredAndOversizedPlans() {
        RegisterExecutionRequest expired = renameRequest(renamePayload(), "NODE_RENAME", NOW.minusSeconds(1));
        ObjectNode oversizedPayload = renamePayload();
        oversizedPayload.put("extra", "x".repeat(2_000));
        ExecutionRegistrationValidator smallValidator = new ExecutionRegistrationValidator(
                objectMapper,
                limits(1_024),
                () -> NOW,
                hasher,
                new BatchSnapshotFingerprint()
        );
        RegisterExecutionRequest oversized = renameRequest(oversizedPayload, "NODE_RENAME", NOW.plusSeconds(300));

        assertError(expired, "invalid_plan_expiry");
        assertThatThrownBy(() -> smallValidator.validate(oversized))
                .isInstanceOf(PlanRegistrationException.class)
                .satisfies(exception -> assertThat(((PlanRegistrationException) exception).statusCode()).isEqualTo(413));
    }

    @Test
    void validatesBatchSnapshotAndRejectsCountOrFingerprintTampering() {
        ObjectNode validPayload = batchMovePayload();
        var validated = validator.validate(renameRequest(
                validPayload, "NODE_BATCH_MOVE", NOW.plusSeconds(300)
        ));
        assertThat(validated.steps().getFirst().actionType().name()).isEqualTo("NODE_BATCH_MOVE");

        ObjectNode wrongCount = validPayload.deepCopy();
        wrongCount.put("snapshotCount", 2);
        assertError(
                renameRequest(wrongCount, "NODE_BATCH_MOVE", NOW.plusSeconds(300)),
                "invalid_action_payload"
        );

        ObjectNode wrongFingerprint = validPayload.deepCopy();
        wrongFingerprint.put("snapshotFingerprint", "0".repeat(64));
        assertError(
                renameRequest(wrongFingerprint, "NODE_BATCH_MOVE", NOW.plusSeconds(300)),
                "batch_snapshot_fingerprint_mismatch"
        );
    }

    @Test
    void validatesUploadWorkflowAndRejectsNonWhitelistedOutputReference() {
        RegisterExecutionRequest valid = uploadWorkflow("nodeId");
        var validated = validator.validate(valid);
        assertThat(validated.steps()).hasSize(2);
        assertThat(validated.steps().get(1).actionType().name()).isEqualTo("UPLOAD_FILES");
        assertThat(validated.steps().get(1).dependsOn()).containsExactly("create_folder");

        assertError(uploadWorkflow("shareCode"), "unsafe_step_output_reference");
    }

    private RegisterExecutionRequest renameRequest(ObjectNode payload, String actionType, Instant expiresAt) {
        RegisterExecutionRequest draft = new RegisterExecutionRequest(
                "action_plan_v2",
                "response-1",
                "conversation-1",
                "file.rename",
                "ap_response-1",
                "",
                "MEDIUM",
                "Rename one node",
                List.of(new RegisterExecutionStepRequest(
                        actionType,
                        "rag_execution_action_v1",
                        payload
                )),
                expiresAt
        );
        return withHash(draft, hasher.planHash(draft));
    }

    private RegisterExecutionRequest uploadWorkflow(String outputField) {
        ObjectNode folder = objectMapper.createObjectNode();
        folder.put("folderName", "uploads");
        ObjectNode upload = objectMapper.createObjectNode();
        ObjectNode reference = upload.putObject("parentIdReference");
        reference.put("stepKey", "create_folder");
        reference.put("outputField", outputField);
        RegisterExecutionRequest draft = new RegisterExecutionRequest(
                "action_plan_v2", "response-upload", "conversation-upload", "folder.create_upload",
                "ap-upload", "", "MEDIUM", "Create a folder and upload files",
                List.of(
                        new RegisterExecutionStepRequest(
                                "create_folder", "FOLDER_CREATE", "rag_execution_action_v1", folder,
                                List.of(), "createdFolder", List.of()
                        ),
                        new RegisterExecutionStepRequest(
                                "upload_files", "UPLOAD_FILES", "rag_execution_action_v1", upload,
                                List.of("create_folder"), "", List.of("files")
                        )
                ),
                NOW.plusSeconds(300)
        );
        return withHash(draft, hasher.planHash(draft));
    }

    private RegisterExecutionRequest withHash(RegisterExecutionRequest request, String hash) {
        return new RegisterExecutionRequest(
                request.planSchemaVersion(), request.sourceResponseId(), request.conversationId(),
                request.intentId(), request.planId(), hash, request.risk(), request.summary(),
                request.steps(), request.expiresAt()
        );
    }

    private ObjectNode renamePayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("nodeId", 42L);
        payload.put("newName", "final.pdf");
        return payload;
    }

    private ObjectNode batchMovePayload() {
        BatchNodeSnapshot snapshot = new BatchNodeSnapshot(
                42L, null, "source.txt", "FILE", "2026-09-18T16:00:00+08:00"
        );
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode item = payload.putArray("items").addObject();
        item.put("nodeId", snapshot.nodeId());
        item.putNull("parentId");
        item.put("name", snapshot.name());
        item.put("nodeType", snapshot.nodeType());
        item.put("updatedAt", snapshot.updatedAt());
        payload.put("snapshotCount", 1);
        payload.put("snapshotFingerprint", new BatchSnapshotFingerprint().hash(List.of(snapshot)));
        payload.put("destinationParentId", 99L);
        return payload;
    }

    private void assertError(RegisterExecutionRequest request, String errorCode) {
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(PlanRegistrationException.class)
                .hasMessage(errorCode);
    }

    private RagExecutionLimitsProperties limits(int maxPlanBytes) {
        return new RagExecutionLimitsProperties(
                10, 100, maxPlanBytes, Duration.ofMinutes(10),
                Duration.ofMinutes(2), Duration.ofMinutes(15), Duration.ofSeconds(30), 3
        );
    }
}
