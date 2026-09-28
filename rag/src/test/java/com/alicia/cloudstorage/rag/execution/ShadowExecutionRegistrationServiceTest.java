package com.alicia.cloudstorage.rag.execution;

import com.alicia.cloudstorage.rag.assistant.ActionPlan;
import com.alicia.cloudstorage.rag.assistant.ActionPlanStep;
import com.alicia.cloudstorage.rag.assistant.ActionPlanBinding;
import com.alicia.cloudstorage.rag.assistant.AssistantConversationSnapshot;
import com.alicia.cloudstorage.rag.assistant.CandidateItem;
import com.alicia.cloudstorage.rag.assistant.IntentRecognitionResponse;
import com.alicia.cloudstorage.rag.assistant.CollectionActionSnapshotStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ShadowExecutionRegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-18T08:00:00Z");
    private static final String SECRET = "phase-two-dedicated-service-secret-123456";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RegistrationPlanHasher hasher = new RegistrationPlanHasher(objectMapper);
    private final RagExecutionRegistrationProperties properties = new RagExecutionRegistrationProperties(
            true,
            "http://localhost:8094",
            SECRET,
            Duration.ofSeconds(2),
            Duration.ofSeconds(5),
            Duration.ofMinutes(10)
    );

    @Test
    void mapsAnEligiblePlanAndAddsOnlyTheReadOnlyReference() {
        AtomicReference<ExecutionRegistrationRequest> captured = new AtomicReference<>();
        Instant expiresAt = NOW.plusSeconds(600);
        ExecutionRegistrationClient client = (request, authorization) -> {
            captured.set(request);
            return new ExecutionRegistrationResponse(
                    "00000000-0000-0000-0000-000000000042",
                    "PENDING_CONFIRMATION",
                    0,
                    expiresAt
            );
        };
        ShadowExecutionRegistrationService service = service(client);

        IntentRecognitionResponse result = service.registerIfEligible(response(), "Bearer user");

        assertThat(result.executionReference()).isNotNull();
        assertThat(result.executionReference().executionId())
                .isEqualTo("00000000-0000-0000-0000-000000000042");
        assertThat(result.withAssistantText("updated").executionReference())
                .isEqualTo(result.executionReference());
        assertThat(captured.get().steps()).singleElement().satisfies(step -> {
            assertThat(step.actionType()).isEqualTo("NODE_RENAME");
            assertThat(step.payload().path("nodeId").asLong()).isEqualTo(42L);
            assertThat(step.payload().path("newName").asText()).isEqualTo("final.pdf");
        });
        assertThat(captured.get().planHash()).isEqualTo(hasher.hash(captured.get()));
    }

    @Test
    void registrationFailureDoesNotChangeTheConversationResponse() {
        ShadowExecutionRegistrationService service = service((request, authorization) -> {
            throw new IllegalStateException("downstream unavailable");
        });
        IntentRecognitionResponse original = response();

        IntentRecognitionResponse result = service.registerIfEligible(original, "Bearer user");

        assertThat(result).isSameAs(original);
        assertThat(result.executionReference()).isNull();
    }

    @Test
    void passwordProtectedShareNeverLeavesTheRagServiceForTaskRegistration() {
        AtomicBoolean registrationCalled = new AtomicBoolean();
        ActionPlan plan = new ActionPlan(
                "action_plan_v2", "ap-share", "review_required", "atomic",
                "share.create", "medium", "candidate_then_final_review", "zh-CN",
                Map.of(),
                List.of(new ActionPlanStep(
                        "share_create", "share.create", "pending",
                        Map.of("nodeIds", List.of(42L), "password", "secret-code"),
                        List.of(), List.of(), "createdShare"
                )),
                List.of(), "Create a protected share", List.of()
        );
        ShadowExecutionRegistrationService service = service((request, authorization) -> {
            registrationCalled.set(true);
            throw new AssertionError("Password must not be sent to ragExecution.");
        });

        IntentRecognitionResponse original = response().withActionPlan(plan);
        IntentRecognitionResponse result = service.registerIfEligible(original, "Bearer user");

        assertThat(result).isSameAs(original);
        assertThat(result.executionReference()).isNull();
        assertThat(registrationCalled).isFalse();
    }

    @Test
    void mapsServerSnapshotIntoTypedBatchMoveRegistration() {
        AtomicReference<ExecutionRegistrationRequest> captured = new AtomicReference<>();
        CollectionActionSnapshotStore store = new CollectionActionSnapshotStore(30, 1000);
        List<CandidateItem> candidates = List.of(
                new CandidateItem(7L, null, "one.txt", "FILE", 10L, "txt", "text/plain",
                        "2026-09-18T16:00:00+08:00"),
                new CandidateItem(8L, null, "two.txt", "FILE", 20L, "txt", "text/plain",
                        "2026-09-18T16:01:00+08:00")
        );
        String snapshotId = store.save("ap-batch", "Bearer user", candidates);
        CandidateItem destination = new CandidateItem(
                99L, null, "archive", "FOLDER", 0L, "", "", "2026-09-18T16:02:00+08:00"
        );
        ActionPlan plan = new ActionPlan(
                "action_plan_v2", "ap-batch", "collection_review_required", "collection",
                "collection.move", "medium", "collection_then_final_review", "zh-CN",
                Map.of(
                        "sourceCollection", new ActionPlanBinding(
                                "sourceCollection", "source_collection", "resolved", "all files", null,
                                candidates, candidates.size(), Map.of("snapshotId", snapshotId)
                        ),
                        "targetParent", new ActionPlanBinding(
                                "targetParent", "target_folder", "resolved", "archive", destination,
                                List.of(destination), 1, Map.of()
                        )
                ),
                List.of(new ActionPlanStep(
                        "move_collection", "node.batch_move", "pending", Map.of(),
                        List.of(), List.of(), "collectionResult"
                )),
                List.of(), "Move two files", List.of()
        );
        ExecutionRegistrationClient client = (request, authorization) -> {
            captured.set(request);
            return new ExecutionRegistrationResponse(
                    "00000000-0000-0000-0000-000000000043",
                    "PENDING_CONFIRMATION", 0, NOW.plusSeconds(600)
            );
        };

        IntentRecognitionResponse result = service(client, store)
                .registerIfEligible(response().withActionPlan(plan), "Bearer user");

        assertThat(result.executionReference()).isNotNull();
        assertThat(captured.get().steps()).singleElement().satisfies(step -> {
            assertThat(step.actionType()).isEqualTo("NODE_BATCH_MOVE");
            assertThat(step.payload().path("snapshotCount").asInt()).isEqualTo(2);
            assertThat(step.payload().path("destinationParentId").asLong()).isEqualTo(99L);
            assertThat(step.payload().path("items").size()).isEqualTo(2);
            assertThat(step.payload().path("snapshotFingerprint").asText()).matches("[0-9a-f]{64}");
        });
    }

    @Test
    void mapsCreateFolderThenUploadIntoServerAndClientWorkflowSteps() {
        AtomicReference<ExecutionRegistrationRequest> captured = new AtomicReference<>();
        CandidateItem destination = new CandidateItem(
                99L, null, "uploads", "FOLDER", 0L, "", "", "2026-09-18T16:02:00+08:00"
        );
        ActionPlan plan = new ActionPlan(
                "action_plan_v2", "ap-upload", "review_required", "composite",
                "composite.create_folder_then_upload", "medium", "conflict_then_final_review", "zh-CN",
                Map.of("targetParent", new ActionPlanBinding(
                        "targetParent", "target_folder", "resolved", "uploads", destination,
                        List.of(destination), 1, Map.of()
                )),
                List.of(
                        new ActionPlanStep(
                                "create_folder", "folder.create", "pending",
                                Map.of("parentId", 99L, "folderName", "September"),
                                List.of(), List.of(), "createdFolder"
                        ),
                        new ActionPlanStep(
                                "upload_files", "file.upload", "pending",
                                Map.of("parentId", "$steps.create_folder.outputs.nodeId"),
                                List.of("create_folder"), List.of(), ""
                        )
                ),
                List.of(), "Create folder and upload", List.of()
        );
        ExecutionRegistrationClient client = (request, authorization) -> {
            captured.set(request);
            return new ExecutionRegistrationResponse(
                    "00000000-0000-0000-0000-000000000044",
                    "PENDING_CONFIRMATION", 0, NOW.plusSeconds(600)
            );
        };

        IntentRecognitionResponse result = service(client)
                .registerIfEligible(response().withActionPlan(plan), "Bearer user");

        assertThat(result.executionReference()).isNotNull();
        assertThat(captured.get().steps()).hasSize(2);
        assertThat(captured.get().steps().get(0).actionType()).isEqualTo("FOLDER_CREATE");
        assertThat(captured.get().steps().get(0).outputKey()).isEqualTo("createdFolder");
        assertThat(captured.get().steps().get(1).actionType()).isEqualTo("UPLOAD_FILES");
        assertThat(captured.get().steps().get(1).dependsOn()).containsExactly("create_folder");
        assertThat(captured.get().steps().get(1).requiredClientFields()).containsExactly("files");
        assertThat(captured.get().steps().get(1).payload().path("parentIdReference").path("outputField").asText())
                .isEqualTo("nodeId");
    }

    private ShadowExecutionRegistrationService service(ExecutionRegistrationClient client) {
        return service(client, new CollectionActionSnapshotStore(30, 1000));
    }

    private ShadowExecutionRegistrationService service(
            ExecutionRegistrationClient client,
            CollectionActionSnapshotStore store
    ) {
        return new ShadowExecutionRegistrationService(
                properties,
                client,
                hasher,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                store,
                new BatchSnapshotFingerprint()
        );
    }

    private IntentRecognitionResponse response() {
        ActionPlan plan = new ActionPlan(
                "action_plan_v2",
                "ap_response-1",
                "review_required",
                "atomic",
                "node.rename",
                "medium",
                "candidate_then_final_review",
                "zh-CN",
                Map.of(),
                List.of(new ActionPlanStep(
                        "node_rename",
                        "node.rename",
                        "pending",
                        Map.of("nodeId", 42L, "name", "final.pdf"),
                        List.of(),
                        List.of(),
                        "renamedNode"
                )),
                List.of(),
                "Rename one node",
                List.of()
        );
        AssistantConversationSnapshot conversation = new AssistantConversationSnapshot(
                "conversation-1", 1, "waiting_for_user_confirmation", "file.rename",
                List.of(), true, "single_candidate", 1, NOW.plusSeconds(1800)
        );
        return new IntentRecognitionResponse(
                "response-1",
                "v1",
                "template",
                "local",
                "local",
                "rename it",
                "file.rename",
                "Rename file",
                "ACTION",
                0.99,
                "rename",
                "rename it",
                Map.of(),
                List.of(),
                List.of(),
                "wait_for_user_confirmation",
                null,
                null,
                null,
                plan,
                "Please confirm",
                "",
                "",
                "",
                null,
                conversation
        );
    }
}
