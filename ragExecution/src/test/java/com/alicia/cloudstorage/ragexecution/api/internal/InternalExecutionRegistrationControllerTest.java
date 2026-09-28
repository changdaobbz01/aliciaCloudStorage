package com.alicia.cloudstorage.ragexecution.api.internal;

import com.alicia.cloudstorage.ragexecution.application.CanonicalJsonHasher;
import com.alicia.cloudstorage.ragexecution.infrastructure.security.ServiceRequestAuthenticator;
import com.alicia.cloudstorage.ragexecution.port.IdentityAccessVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:registration_api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "alicia.rag-execution.features.enabled=true",
        "alicia.rag-execution.features.registration-enabled=true",
        "alicia.rag-execution.security.service-secret=phase-two-dedicated-service-secret-123456"
})
@AutoConfigureMockMvc
class InternalExecutionRegistrationControllerTest {

    private static final String SECRET = "phase-two-dedicated-service-secret-123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CanonicalJsonHasher hasher;

    @MockitoBean
    private IdentityAccessVerifier identityAccessVerifier;

    @Test
    void acceptsSignedRegistrationAndRejectsTheSameNonceReplay() throws Exception {
        when(identityAccessVerifier.requireRagAccess(anyString()))
                .thenReturn(new IdentityAccessVerifier.IdentityPrincipal(42L, "RAG_USER"));
        byte[] body = objectMapper.writeValueAsBytes(request());
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = "nonce-controller-1234567890";
        String bodyHash = ServiceRequestAuthenticator.sha256Hex(body);
        String signature = ServiceRequestAuthenticator.hmacSha256Hex(
                SECRET,
                ServiceRequestAuthenticator.canonical(
                        "POST", "/internal/executions", "rag", timestamp, nonce, bodyHash
                )
        );

        mockMvc.perform(post("/internal/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test")
                        .header(ServiceRequestAuthenticator.CALLER_HEADER, "rag")
                        .header(ServiceRequestAuthenticator.TIMESTAMP_HEADER, timestamp)
                        .header(ServiceRequestAuthenticator.NONCE_HEADER, nonce)
                        .header(ServiceRequestAuthenticator.BODY_SHA256_HEADER, bodyHash)
                        .header(ServiceRequestAuthenticator.SIGNATURE_HEADER, signature)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(post("/internal/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test")
                        .header(ServiceRequestAuthenticator.CALLER_HEADER, "rag")
                        .header(ServiceRequestAuthenticator.TIMESTAMP_HEADER, timestamp)
                        .header(ServiceRequestAuthenticator.NONCE_HEADER, nonce)
                        .header(ServiceRequestAuthenticator.BODY_SHA256_HEADER, bodyHash)
                        .header(ServiceRequestAuthenticator.SIGNATURE_HEADER, signature)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("replayed_service_signature"));
    }

    private RegisterExecutionRequest request() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("nodeId", 42L);
        payload.put("newName", "final.pdf");
        RegisterExecutionRequest draft = new RegisterExecutionRequest(
                "action_plan_v2", "response-api", "conversation-api", "file.rename",
                "plan-api", "", "MEDIUM", "Rename one node",
                List.of(new RegisterExecutionStepRequest(
                        "NODE_RENAME", "rag_execution_action_v1", payload
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
