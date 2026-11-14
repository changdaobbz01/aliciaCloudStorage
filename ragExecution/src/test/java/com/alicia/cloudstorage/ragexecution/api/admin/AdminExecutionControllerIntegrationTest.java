package com.alicia.cloudstorage.ragexecution.api.admin;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionEventVisibility;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionRisk;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionStatus;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionEventJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionEventJpaRepository;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaRepository;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionStepJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionStepJpaRepository;
import com.alicia.cloudstorage.ragexecution.port.CloudActionGateway;
import com.alicia.cloudstorage.ragexecution.port.IdentityAccessVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_execution;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "alicia.rag-execution.features.enabled=true"
})
@AutoConfigureMockMvc
class AdminExecutionControllerIntegrationTest {

    private static final String AUTHORIZATION = "Bearer admin-token";

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
    void ragUserCannotReadTheAdminControlPlane() throws Exception {
        when(identityAccessVerifier.requireRagAccess(AUTHORIZATION))
                .thenReturn(new IdentityAccessVerifier.IdentityPrincipal(42L, "RAG_USER"));

        mockMvc.perform(get("/api/admin/executions/overview")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("rag_admin_observability_required"));
    }

    @Test
    void adminCanFilterExecutionsAcrossOwnersWithoutReceivingPrivatePayloads() throws Exception {
        UUID activeId = createExecution(42L, false, "private-active-folder");
        UUID cancelledId = createExecution(84L, true, "private-cancelled-folder");
        when(identityAccessVerifier.requireRagAccess(AUTHORIZATION))
                .thenReturn(new IdentityAccessVerifier.IdentityPrincipal(7L, "RAG_ADMIN"));

        mockMvc.perform(get("/api/admin/executions/overview")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExecutions").value(2))
                .andExpect(jsonPath("$.activeExecutions").value(1))
                .andExpect(jsonPath("$.attentionExecutions").value(0))
                .andExpect(jsonPath("$.statusCounts.PENDING_CONFIRMATION").value(1))
                .andExpect(jsonPath("$.statusCounts.CANCELLED").value(1));

        String page = mockMvc.perform(get("/api/admin/executions")
                        .queryParam("status", "cancelled")
                        .queryParam("ownerUserId", "84")
                        .queryParam("page", "1")
                        .queryParam("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].executionId").value(cancelledId.toString()))
                .andExpect(jsonPath("$.items[0].ownerUserId").value(84))
                .andExpect(jsonPath("$.items[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.items[0].actionTypes[0]").value("FOLDER_CREATE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(page)
                .doesNotContain("private-active-folder", "private-cancelled-folder", "payload", "planHash");

        mockMvc.perform(get("/api/admin/executions/{id}", activeId)
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution.executionId").value(activeId.toString()))
                .andExpect(jsonPath("$.steps[0].actionType").value("FOLDER_CREATE"))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].type").value("EXECUTION_REGISTERED"));
    }

    @Test
    void adminQueriesRejectInvalidFiltersAndIdentifiers() throws Exception {
        when(identityAccessVerifier.requireRagAccess(AUTHORIZATION))
                .thenReturn(new IdentityAccessVerifier.IdentityPrincipal(7L, "RAG_ADMIN"));

        mockMvc.perform(get("/api/admin/executions")
                        .queryParam("status", "UNKNOWN")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_execution_status"));

        mockMvc.perform(get("/api/admin/executions/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_execution_id"));

        mockMvc.perform(get("/api/admin/executions")
                        .queryParam("ownerUserId", "0")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_owner_user_id"));

        mockMvc.perform(get("/api/admin/executions")
                        .queryParam("page", "0")
                        .queryParam("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_page_request"));
    }

    private UUID createExecution(long ownerId, boolean cancelled, String privateFolderName) {
        Instant now = Instant.now();
        UUID executionId = UUID.randomUUID();
        ExecutionJpaEntity execution = ExecutionJpaEntity.createPending(
                executionId,
                ownerId,
                "private-conversation-" + executionId,
                "private-response-" + executionId,
                "plan-" + executionId,
                "action_plan_v2",
                "a".repeat(64),
                "Private summary for " + privateFolderName,
                ExecutionRisk.LOW,
                "plan:" + executionId,
                now.plusSeconds(600),
                now
        );
        if (cancelled) {
            execution.transitionTo(ExecutionStatus.CANCELLED, now.plusSeconds(1));
        }
        executionRepository.saveAndFlush(execution);

        ObjectNode payload = objectMapper.createObjectNode().put("folderName", privateFolderName);
        stepRepository.saveAndFlush(ExecutionStepJpaEntity.createPending(
                UUID.randomUUID(),
                executionId,
                0,
                ExecutionActionType.FOLDER_CREATE,
                "rag_execution_action_v1",
                payload,
                "b".repeat(64),
                now
        ));
        eventRepository.saveAndFlush(ExecutionEventJpaEntity.create(
                executionId,
                1,
                "EXECUTION_REGISTERED",
                ExecutionEventVisibility.PUBLIC,
                objectMapper.createObjectNode().put("status", "PENDING_CONFIRMATION"),
                now
        ));
        eventRepository.saveAndFlush(ExecutionEventJpaEntity.create(
                executionId,
                2,
                "INTERNAL_DIAGNOSTIC",
                ExecutionEventVisibility.INTERNAL,
                objectMapper.createObjectNode().put("private", privateFolderName),
                now.plusMillis(1)
        ));
        return executionId;
    }
}
