package com.alicia.cloudstorage.ragexecution.api.publicapi;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionRisk;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionEventJpaRepository;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaRepository;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionStepJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionStepJpaRepository;
import com.alicia.cloudstorage.ragexecution.port.IdentityAccessVerifier;
import com.alicia.cloudstorage.ragexecution.port.CloudActionGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:public_execution;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "alicia.rag-execution.features.enabled=true",
        "alicia.rag-execution.features.public-confirm-enabled=true",
        "alicia.rag-execution.features.admin-only=true",
        "alicia.rag-execution.features.allowed-actions=NODE_RENAME,FOLDER_CREATE,UPLOAD_FILES"
})
@AutoConfigureMockMvc
class PublicExecutionControllerIntegrationTest {

    private static final String AUTHORIZATION = "Bearer test-token";
    private static final long OWNER_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExecutionJpaRepository executionRepository;

    @Autowired
    private ExecutionStepJpaRepository stepRepository;

    @Autowired
    private ExecutionEventJpaRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IdentityAccessVerifier identityAccessVerifier;

    @MockitoBean
    private CloudActionGateway cloudActionGateway;

    @BeforeEach
    void resetDatabase() {
        eventRepository.deleteAll();
        stepRepository.deleteAll();
        executionRepository.deleteAll();
    }

    @Test
    void adminConfirmationIsIdempotentAndDoesNotDuplicateQueueEvents() throws Exception {
        UUID executionId = createPending(OWNER_ID);
        when(identityAccessVerifier.requireRagAccess(AUTHORIZATION))
                .thenReturn(new IdentityAccessVerifier.IdentityPrincipal(OWNER_ID, "RAG_ADMIN"));

        mockMvc.perform(post("/api/executions/{id}/confirm", executionId)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.steps[0].actionType").value("NODE_RENAME"));

        mockMvc.perform(post("/api/executions/{id}/confirm", executionId)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"));

        assertThat(eventRepository.findByExecutionIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(
                executionId.toString(), 0
        )).hasSize(1);
    }

    @Test
    void regularRagUserCannotConfirmDuringAdminRollout() throws Exception {
        UUID executionId = createPending(OWNER_ID);
        when(identityAccessVerifier.requireRagAccess(AUTHORIZATION))
                .thenReturn(new IdentityAccessVerifier.IdentityPrincipal(OWNER_ID, "RAG_USER"));
        mockMvc.perform(post("/api/executions/{id}/confirm", executionId)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("rag_admin_confirmation_required"));
    }

    @Test
    void anotherUserCannotReadOrCancelTheExecution() throws Exception {
        UUID executionId = createPending(OWNER_ID);
        when(identityAccessVerifier.requireRagAccess(AUTHORIZATION))
                .thenReturn(new IdentityAccessVerifier.IdentityPrincipal(99L, "RAG_ADMIN"));

        mockMvc.perform(get("/api/executions/{id}", executionId)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("execution_not_found"));

        mockMvc.perform(post("/api/executions/{id}/cancel", executionId)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanCancelAndReadEventsWithoutReceivingTheActionPayload() throws Exception {
        UUID executionId = createPending(OWNER_ID);
        when(identityAccessVerifier.requireRagAccess(AUTHORIZATION))
                .thenReturn(new IdentityAccessVerifier.IdentityPrincipal(OWNER_ID, "RAG_USER"));

        mockMvc.perform(post("/api/executions/{id}/cancel", executionId)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.steps[0].result").doesNotExist());

        String events = mockMvc.perform(get("/api/executions/{id}/events", executionId)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("EXECUTION_CANCELLED"))
                .andExpect(jsonPath("$[0].payload.status").value("CANCELLED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(events).doesNotContain("private-name.txt", "newName");
    }

    @Test
    void streamResumesFromAnEventSequenceAndClosesAtTerminalState() throws Exception {
        UUID executionId = createPending(OWNER_ID);
        when(identityAccessVerifier.requireRagAccess(AUTHORIZATION))
                .thenReturn(new IdentityAccessVerifier.IdentityPrincipal(OWNER_ID, "RAG_USER"));
        mockMvc.perform(post("/api/executions/{id}/cancel", executionId)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk());

        MvcResult started = mockMvc.perform(get("/api/executions/{id}/stream", executionId)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("Last-Event-ID", "0"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("EXECUTION_CANCELLED")));
    }

    @Test
    void ownerCompletesWaitingUploadInputWithTypedNodeResults() throws Exception {
        UUID executionId = createWaitingUpload(OWNER_ID);
        ExecutionJpaEntity waiting = executionRepository.findById(executionId.toString()).orElseThrow();
        ExecutionStepJpaEntity upload = stepRepository.findByExecutionIdOrderByStepIndexAsc(
                executionId.toString()
        ).get(1);
        when(identityAccessVerifier.requireRagAccess(AUTHORIZATION))
                .thenReturn(new IdentityAccessVerifier.IdentityPrincipal(OWNER_ID, "RAG_USER"));
        when(cloudActionGateway.dispatch(any())).thenReturn(new CloudActionGateway.CloudActionResult(
                "UPLOADS_VERIFIED",
                objectMapper.createObjectNode().put("uploadedCount", 2),
                Instant.now()
        ));

        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "expectedVersion", waiting.getVersion(),
                "stepId", upload.getId().toString(),
                "status", "SUCCEEDED",
                "nodeIds", java.util.List.of(501L, 502L)
        ));
        mockMvc.perform(post("/api/executions/{id}/client-input", executionId)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultCode").value("WORKFLOW_SUCCEEDED"))
                .andExpect(jsonPath("$.steps[1].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[1].result.uploadedCount").value(2))
                .andExpect(jsonPath("$.steps[1].result.nodeIds[0]").value(501));
        verify(cloudActionGateway).dispatch(argThat(command ->
                command.actorUserId() == OWNER_ID
                        && command.actionType() == ExecutionActionType.UPLOAD_FILES
                        && command.payload().path("parentId").asLong() == 99L
                        && command.payload().path("nodeIds").size() == 2
        ));
        mockMvc.perform(get("/api/executions/{id}/events", executionId)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("STEP_SUCCEEDED"))
                .andExpect(jsonPath("$[0].payload.actionType").value("UPLOAD_FILES"))
                .andExpect(jsonPath("$[1].type").value("CLIENT_INPUT_COMPLETED"))
                .andExpect(jsonPath("$[2].type").value("EXECUTION_SUCCEEDED"));
    }

    private UUID createPending(long ownerId) {
        Instant now = Instant.now();
        UUID executionId = UUID.randomUUID();
        ExecutionJpaEntity execution = ExecutionJpaEntity.createPending(
                executionId,
                ownerId,
                "conversation-1",
                "response-1",
                "plan-" + executionId,
                "action_plan_v2",
                "a".repeat(64),
                "Rename the selected item",
                ExecutionRisk.MEDIUM,
                "plan:" + executionId,
                now.plusSeconds(600),
                now
        );
        executionRepository.saveAndFlush(execution);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("nodeId", 7);
        payload.put("expectedNodeVersion", 0);
        payload.put("newName", "private-name.txt");
        stepRepository.saveAndFlush(ExecutionStepJpaEntity.createPending(
                UUID.randomUUID(),
                executionId,
                0,
                ExecutionActionType.NODE_RENAME,
                "rag_execution_action_v1",
                payload,
                "b".repeat(64),
                now
        ));
        return executionId;
    }

    private UUID createWaitingUpload(long ownerId) {
        Instant now = Instant.now();
        UUID executionId = UUID.randomUUID();
        ExecutionJpaEntity execution = ExecutionJpaEntity.createPending(
                executionId, ownerId, "conversation-upload", "response-upload", "plan-" + executionId,
                "action_plan_v2", "c".repeat(64), "Create folder and upload", ExecutionRisk.MEDIUM,
                "plan:" + executionId, now.plusSeconds(600), now
        );
        execution.confirmAndQueue(now, now.plusSeconds(120));
        execution.claimLease("test-worker", now.plusSeconds(1), now.plusSeconds(31));
        execution.waitForClientInput(now.plusSeconds(2), now.plusSeconds(900));
        executionRepository.saveAndFlush(execution);

        ObjectNode folderPayload = objectMapper.createObjectNode().put("folderName", "September");
        ExecutionStepJpaEntity folder = ExecutionStepJpaEntity.createPending(
                UUID.randomUUID(), executionId, 0, "create_folder", ExecutionActionType.FOLDER_CREATE,
                "rag_execution_action_v1", folderPayload, "d".repeat(64), java.util.List.of(),
                "createdFolder", java.util.List.of(), now
        );
        folder.start(now.plusSeconds(1));
        folder.succeed(objectMapper.createObjectNode().put("nodeId", 99L), now.plusSeconds(2));
        stepRepository.saveAndFlush(folder);

        ObjectNode uploadPayload = objectMapper.createObjectNode();
        uploadPayload.putObject("parentIdReference")
                .put("stepKey", "create_folder")
                .put("outputField", "nodeId");
        ExecutionStepJpaEntity upload = ExecutionStepJpaEntity.createPending(
                UUID.randomUUID(), executionId, 1, "upload_files", ExecutionActionType.UPLOAD_FILES,
                "rag_execution_action_v1", uploadPayload, "e".repeat(64), java.util.List.of("create_folder"),
                "", java.util.List.of("files"), now
        );
        upload.start(now.plusSeconds(1));
        upload.waitForClientInput(
                objectMapper.createObjectNode().put("inputType", "UPLOAD_FILES").put("parentId", 99L),
                now.plusSeconds(2)
        );
        stepRepository.saveAndFlush(upload);
        return executionId;
    }
}
