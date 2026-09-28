package com.alicia.cloudstorage.ragexecution.integration;

import com.alicia.cloudstorage.ragexecution.api.internal.RegisterExecutionRequest;
import com.alicia.cloudstorage.ragexecution.api.internal.RegisterExecutionResponse;
import com.alicia.cloudstorage.ragexecution.api.internal.RegisterExecutionStepRequest;
import com.alicia.cloudstorage.ragexecution.application.CanonicalJsonHasher;
import com.alicia.cloudstorage.ragexecution.application.InternalExecutionRegistrationService;
import com.alicia.cloudstorage.ragexecution.application.PlanRegistrationException;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionEventJpaRepository;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaRepository;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionStepJpaRepository;
import com.alicia.cloudstorage.ragexecution.port.IdentityAccessVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:registration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=validate",
                "alicia.rag-execution.features.enabled=true",
                "alicia.rag-execution.features.registration-enabled=true",
                "alicia.rag-execution.security.service-secret=phase-two-dedicated-service-secret-123456"
        }
)
class ExecutionRegistrationIntegrationTest {

    @Autowired
    private InternalExecutionRegistrationService service;

    @Autowired
    private CanonicalJsonHasher hasher;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExecutionJpaRepository executionRepository;

    @Autowired
    private ExecutionStepJpaRepository stepRepository;

    @Autowired
    private ExecutionEventJpaRepository eventRepository;

    @MockitoBean
    private IdentityAccessVerifier identityAccessVerifier;

    @Test
    void duplicateRegistrationReturnsOneDurableExecution() {
        when(identityAccessVerifier.requireRagAccess("Bearer test"))
                .thenReturn(new IdentityAccessVerifier.IdentityPrincipal(42L, "RAG_USER"));
        RegisterExecutionRequest request = request();

        RegisterExecutionResponse first = service.register(request, "Bearer test");
        RegisterExecutionResponse duplicate = service.register(request, "Bearer test");

        assertThat(duplicate).isEqualTo(first);
        assertThat(executionRepository.count()).isEqualTo(1L);
        assertThat(stepRepository.findByExecutionIdOrderByStepIndexAsc(first.executionId())).hasSize(1);
        assertThat(eventRepository.findByExecutionIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(
                first.executionId(), 0
        )).hasSize(1);
    }

    @Test
    void samePlanIdCannotBeReusedWithDifferentContent() {
        when(identityAccessVerifier.requireRagAccess("Bearer test"))
                .thenReturn(new IdentityAccessVerifier.IdentityPrincipal(42L, "RAG_USER"));
        RegisterExecutionRequest original = request();
        service.register(original, "Bearer test");

        RegisterExecutionRequest changedDraft = new RegisterExecutionRequest(
                original.planSchemaVersion(), original.sourceResponseId(), original.conversationId(),
                original.intentId(), original.planId(), "", original.risk(), "Changed summary",
                original.steps(), original.expiresAt()
        );
        RegisterExecutionRequest changed = new RegisterExecutionRequest(
                changedDraft.planSchemaVersion(), changedDraft.sourceResponseId(),
                changedDraft.conversationId(), changedDraft.intentId(), changedDraft.planId(),
                hasher.planHash(changedDraft), changedDraft.risk(), changedDraft.summary(),
                changedDraft.steps(), changedDraft.expiresAt()
        );

        assertThatThrownBy(() -> service.register(changed, "Bearer test"))
                .isInstanceOf(PlanRegistrationException.class)
                .hasMessageContaining("plan_hash_conflict");
        assertThat(executionRepository.count()).isEqualTo(1L);
    }

    private RegisterExecutionRequest request() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("nodeId", 42L);
        payload.put("newName", "final.pdf");
        RegisterExecutionRequest draft = new RegisterExecutionRequest(
                "action_plan_v2",
                "response-integration",
                "conversation-integration",
                "file.rename",
                "plan-integration",
                "",
                "MEDIUM",
                "Rename one node",
                List.of(new RegisterExecutionStepRequest(
                        "NODE_RENAME",
                        "rag_execution_action_v1",
                        payload
                )),
                Instant.now().plusSeconds(300)
        );
        return new RegisterExecutionRequest(
                draft.planSchemaVersion(), draft.sourceResponseId(), draft.conversationId(),
                draft.intentId(), draft.planId(), hasher.planHash(draft), draft.risk(), draft.summary(),
                draft.steps(), draft.expiresAt()
        );
    }
}
