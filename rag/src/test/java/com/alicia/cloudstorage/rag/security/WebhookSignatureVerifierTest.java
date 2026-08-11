package com.alicia.cloudstorage.rag.security;

import com.alicia.cloudstorage.rag.config.RagSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSignatureVerifierTest {

    private WebhookSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        RagSecurityProperties properties = new RagSecurityProperties();
        properties.setWebhookSecret("test-webhook-secret");
        verifier = new WebhookSignatureVerifier(properties);
    }

    @Test
    void verifyAcceptsValidSignature() {
        String body = "{\"eventId\":\"event-1\"}";
        String signature = "sha256=" + verifier.sign(body);

        verifier.verify(body, signature);
    }

    @Test
    void verifyRejectsInvalidSignature() {
        assertThatThrownBy(() -> verifier.verify("{\"eventId\":\"event-1\"}", "sha256=bad"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
    }
}
