package com.alicia.cloudstorage.ragexecution.infrastructure.security;

import com.alicia.cloudstorage.ragexecution.config.RagExecutionSecurityProperties;
import com.alicia.cloudstorage.ragexecution.port.ServiceNonceStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceRequestAuthenticatorTest {

    private static final String SECRET = "phase-two-dedicated-service-secret-123456";
    private static final Instant NOW = Instant.parse("2026-09-18T08:00:00Z");

    private final InMemoryNonceStore nonceStore = new InMemoryNonceStore();
    private final ServiceRequestAuthenticator authenticator = new ServiceRequestAuthenticator(
            new RagExecutionSecurityProperties(SECRET, Duration.ofSeconds(60), Duration.ofMinutes(3)),
            () -> NOW,
            nonceStore
    );

    @Test
    void acceptsAValidSignatureAndRejectsItsReplay() {
        SignedRequest request = signed("nonce-1234567890123456", NOW, "{\"plan\":1}");

        assertThatCode(() -> authenticate(request)).doesNotThrowAnyException();
        assertThatThrownBy(() -> authenticate(request))
                .isInstanceOf(ServiceRequestAuthenticationException.class)
                .hasMessage("replayed_service_signature");
    }

    @Test
    void rejectsBodyTamperingAndExpiredTimestamps() {
        SignedRequest valid = signed("nonce-2234567890123456", NOW, "{\"plan\":1}");
        SignedRequest tampered = new SignedRequest(
                valid.timestamp(), valid.nonce(), valid.bodyHash(), valid.signature(), "{\"plan\":2}".getBytes(StandardCharsets.UTF_8)
        );
        SignedRequest expired = signed("nonce-3234567890123456", NOW.minusSeconds(61), "{\"plan\":1}");

        assertThatThrownBy(() -> authenticate(tampered))
                .isInstanceOf(ServiceRequestAuthenticationException.class)
                .hasMessage("content_digest_mismatch");
        assertThatThrownBy(() -> authenticate(expired))
                .isInstanceOf(ServiceRequestAuthenticationException.class)
                .hasMessage("expired_service_signature");
    }

    private void authenticate(SignedRequest request) {
        authenticator.authenticate(
                "POST",
                "/internal/executions",
                "rag",
                request.timestamp(),
                request.nonce(),
                request.bodyHash(),
                request.signature(),
                request.body()
        );
    }

    private SignedRequest signed(String nonce, Instant issuedAt, String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(issuedAt.getEpochSecond());
        String bodyHash = ServiceRequestAuthenticator.sha256Hex(body);
        String canonical = ServiceRequestAuthenticator.canonical(
                "POST", "/internal/executions", "rag", timestamp, nonce, bodyHash
        );
        return new SignedRequest(
                timestamp,
                nonce,
                bodyHash,
                ServiceRequestAuthenticator.hmacSha256Hex(SECRET, canonical),
                body
        );
    }

    private record SignedRequest(
            String timestamp,
            String nonce,
            String bodyHash,
            String signature,
            byte[] body
    ) {
    }

    private static final class InMemoryNonceStore implements ServiceNonceStore {
        private final Set<String> consumed = new HashSet<>();

        @Override
        public void consume(String callerService, String nonce, Instant expiresAt, Instant now) {
            if (!consumed.add(callerService + ":" + nonce)) {
                throw new IllegalStateException("replay");
            }
        }
    }
}
