package com.alicia.cloudstorage.ragexecution.worker;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionRisk;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionStatus;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionStepStatus;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionEventJpaRepository;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaRepository;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionLeasePersistenceAdapter;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionStepJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionStepJpaRepository;
import com.alicia.cloudstorage.ragexecution.port.CloudActionGateway.CloudActionCommand;
import com.alicia.cloudstorage.ragexecution.port.CloudActionGateway.CloudActionResult;
import com.alicia.cloudstorage.ragexecution.port.ExecutionClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:worker_state;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "alicia.rag-execution.features.allowed-actions=NODE_RENAME,FOLDER_CREATE,UPLOAD_FILES",
        "spring.main.web-application-type=none"
})
class ExecutionWorkStateIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-09-18T08:00:00Z");
    private static final String WORKER = "test-worker";

    @Autowired
    private ExecutionJpaRepository executionRepository;

    @Autowired
    private ExecutionStepJpaRepository stepRepository;

    @Autowired
    private ExecutionEventJpaRepository eventRepository;

    @Autowired
    private ExecutionLeasePersistenceAdapter leaseStore;

    @Autowired
    private ExecutionWorkStateService stateService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExecutionClock clock;

    @BeforeEach
    void resetDatabase() {
        eventRepository.deleteAll();
        stepRepository.deleteAll();
        executionRepository.deleteAll();
    }

    @Test
    void retryThenReceiptSuccessConvergesToOneTerminalExecution() {
        UUID executionId = createQueued();
        when(clock.now()).thenReturn(BASE.plusSeconds(1));
        leaseStore.claimNext(WORKER, BASE.plusSeconds(1), Duration.ofSeconds(30));
        CloudActionCommand first = stateService.prepare(executionId, WORKER).orElseThrow();

        when(clock.now()).thenReturn(BASE.plusSeconds(2));
        stateService.fail(executionId, first.stepId(), WORKER, "cloud_response_unknown", true);
        assertStatus(executionId, ExecutionStatus.RETRY_WAIT, ExecutionStepStatus.RETRY_WAIT, 1);

        when(clock.now()).thenReturn(BASE.plusSeconds(5));
        assertThat(stateService.requeueOneReadyRetry()).isTrue();
        leaseStore.claimNext(WORKER, BASE.plusSeconds(5), Duration.ofSeconds(30));
        CloudActionCommand second = stateService.prepare(executionId, WORKER).orElseThrow();
        assertThat(second.stepId()).isEqualTo(first.stepId());

        ObjectNode result = objectMapper.createObjectNode().put("nodeId", 7).put("entityVersion", 1);
        when(clock.now()).thenReturn(BASE.plusSeconds(6));
        stateService.succeed(
                executionId,
                second.stepId(),
                WORKER,
                new CloudActionResult("NODE_RENAMED", result, BASE.plusSeconds(6))
        );

        assertStatus(executionId, ExecutionStatus.SUCCEEDED, ExecutionStepStatus.SUCCEEDED, 2);
    }

    @Test
    void expiredRunningLeaseReusesTheSameStepWithoutIncreasingAttempts() {
        UUID executionId = createQueued();
        when(clock.now()).thenReturn(BASE.plusSeconds(1));
        leaseStore.claimNext(WORKER, BASE.plusSeconds(1), Duration.ofSeconds(30));
        CloudActionCommand first = stateService.prepare(executionId, WORKER).orElseThrow();

        String recoveryWorker = "recovery-worker";
        leaseStore.claimNext(recoveryWorker, BASE.plusSeconds(32), Duration.ofSeconds(30));
        when(clock.now()).thenReturn(BASE.plusSeconds(32));
        CloudActionCommand recovered = stateService.prepare(executionId, recoveryWorker).orElseThrow();

        assertThat(recovered.stepId()).isEqualTo(first.stepId());
        assertThat(stepRepository.findById(first.stepId().toString()).orElseThrow().getAttempts()).isEqualTo(1);
    }

    @Test
    void expiredRunningExecutionIsClosedInsteadOfReclaimed() {
        UUID executionId = createQueued();
        when(clock.now()).thenReturn(BASE.plusSeconds(1));
        leaseStore.claimNext(WORKER, BASE.plusSeconds(1), Duration.ofSeconds(30));
        CloudActionCommand command = stateService.prepare(executionId, WORKER).orElseThrow();

        Instant afterDeadline = BASE.plusSeconds(121);
        assertThat(leaseStore.claimNext("recovery-worker", afterDeadline, Duration.ofSeconds(30))).isEmpty();
        when(clock.now()).thenReturn(afterDeadline);
        assertThat(stateService.expireOne()).isTrue();

        assertStatus(executionId, ExecutionStatus.EXPIRED, ExecutionStepStatus.RUNNING, 1);
        assertThat(eventRepository.findByExecutionIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(
                executionId.toString(), 0
        ))
                .anySatisfy(event -> assertThat(event.getEventType()).isEqualTo("EXECUTION_EXPIRED"));
        assertThat(command.stepId()).isNotNull();
    }

    @Test
    void uploadWorkflowResolvesWhitelistedOutputAndExpiresWithoutClientInput() {
        UUID executionId = createQueuedUploadWorkflow();
        when(clock.now()).thenReturn(BASE.plusSeconds(1));
        leaseStore.claimNext(WORKER, BASE.plusSeconds(1), Duration.ofSeconds(30));
        CloudActionCommand folder = stateService.prepare(executionId, WORKER).orElseThrow();
        assertThat(folder.actionType()).isEqualTo(ExecutionActionType.FOLDER_CREATE);

        ObjectNode folderResult = objectMapper.createObjectNode().put("nodeId", 99L).put("entityVersion", 0L);
        when(clock.now()).thenReturn(BASE.plusSeconds(2));
        stateService.succeed(
                executionId,
                folder.stepId(),
                WORKER,
                new CloudActionResult("FOLDER_CREATED", folderResult, BASE.plusSeconds(2))
        );
        assertThat(executionRepository.findById(executionId.toString()).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.QUEUED);

        when(clock.now()).thenReturn(BASE.plusSeconds(3));
        leaseStore.claimNext(WORKER, BASE.plusSeconds(3), Duration.ofSeconds(30));
        assertThat(stateService.prepare(executionId, WORKER)).isEmpty();
        ExecutionJpaEntity waiting = executionRepository.findById(executionId.toString()).orElseThrow();
        var steps = stepRepository.findByExecutionIdOrderByStepIndexAsc(executionId.toString());
        assertThat(waiting.getStatus()).isEqualTo(ExecutionStatus.WAITING_CLIENT_INPUT);
        assertThat(steps.get(0).getStatus()).isEqualTo(ExecutionStepStatus.SUCCEEDED);
        assertThat(steps.get(1).getStatus()).isEqualTo(ExecutionStepStatus.WAITING_CLIENT_INPUT);
        assertThat(steps.get(1).getResult().path("parentId").asLong()).isEqualTo(99L);

        when(clock.now()).thenReturn(BASE.plus(Duration.ofMinutes(16)));
        assertThat(stateService.expireOne()).isTrue();
        assertThat(executionRepository.findById(executionId.toString()).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.EXPIRED);
        ExecutionStepJpaEntity expiredInput = stepRepository.findByExecutionIdOrderByStepIndexAsc(
                executionId.toString()
        ).get(1);
        assertThat(expiredInput.getStatus()).isEqualTo(ExecutionStepStatus.FAILED);
        assertThat(expiredInput.getErrorCode()).isEqualTo("client_input_timeout");
    }

    private UUID createQueued() {
        UUID executionId = UUID.randomUUID();
        ExecutionJpaEntity execution = ExecutionJpaEntity.createPending(
                executionId,
                42L,
                "conversation-1",
                "response-1",
                "plan-" + executionId,
                "action_plan_v2",
                "a".repeat(64),
                "Rename the selected item",
                ExecutionRisk.MEDIUM,
                "plan:" + executionId,
                BASE.plusSeconds(600),
                BASE
        );
        execution.confirmAndQueue(BASE, BASE.plusSeconds(120));
        executionRepository.saveAndFlush(execution);
        ObjectNode payload = objectMapper.createObjectNode()
                .put("nodeId", 7)
                .put("expectedNodeVersion", 0)
                .put("newName", "renamed.txt");
        stepRepository.saveAndFlush(ExecutionStepJpaEntity.createPending(
                UUID.randomUUID(),
                executionId,
                0,
                ExecutionActionType.NODE_RENAME,
                "rag_execution_action_v1",
                payload,
                "b".repeat(64),
                BASE
        ));
        return executionId;
    }

    private UUID createQueuedUploadWorkflow() {
        UUID executionId = UUID.randomUUID();
        ExecutionJpaEntity execution = ExecutionJpaEntity.createPending(
                executionId, 42L, "conversation-upload", "response-upload", "plan-" + executionId,
                "action_plan_v2", "c".repeat(64), "Create folder and upload", ExecutionRisk.MEDIUM,
                "plan:" + executionId, BASE.plusSeconds(600), BASE
        );
        execution.confirmAndQueue(BASE, BASE.plusSeconds(120));
        executionRepository.saveAndFlush(execution);
        ObjectNode folder = objectMapper.createObjectNode().put("folderName", "September");
        stepRepository.saveAndFlush(ExecutionStepJpaEntity.createPending(
                UUID.randomUUID(), executionId, 0, "create_folder", ExecutionActionType.FOLDER_CREATE,
                "rag_execution_action_v1", folder, "d".repeat(64), java.util.List.of(),
                "createdFolder", java.util.List.of(), BASE
        ));
        ObjectNode upload = objectMapper.createObjectNode();
        upload.putObject("parentIdReference")
                .put("stepKey", "create_folder")
                .put("outputField", "nodeId");
        stepRepository.saveAndFlush(ExecutionStepJpaEntity.createPending(
                UUID.randomUUID(), executionId, 1, "upload_files", ExecutionActionType.UPLOAD_FILES,
                "rag_execution_action_v1", upload, "e".repeat(64), java.util.List.of("create_folder"),
                "", java.util.List.of("files"), BASE
        ));
        return executionId;
    }

    private void assertStatus(
            UUID executionId,
            ExecutionStatus executionStatus,
            ExecutionStepStatus stepStatus,
            int attempts
    ) {
        ExecutionJpaEntity execution = executionRepository.findById(executionId.toString()).orElseThrow();
        ExecutionStepJpaEntity step = stepRepository.findByExecutionIdOrderByStepIndexAsc(
                executionId.toString()
        ).getFirst();
        assertThat(execution.getStatus()).isEqualTo(executionStatus);
        assertThat(step.getStatus()).isEqualTo(stepStatus);
        assertThat(step.getAttempts()).isEqualTo(attempts);
    }
}
