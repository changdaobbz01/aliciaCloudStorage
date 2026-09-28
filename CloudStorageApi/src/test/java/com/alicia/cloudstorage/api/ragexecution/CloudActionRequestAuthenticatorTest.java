package com.alicia.cloudstorage.api.ragexecution;

import com.alicia.cloudstorage.api.ragexecution.config.RagExecutionCloudActionProperties;
import com.alicia.cloudstorage.api.ragexecution.persistence.RagActionNonceStore;
import com.alicia.cloudstorage.api.ragexecution.security.CloudActionAuthenticationException;
import com.alicia.cloudstorage.api.ragexecution.security.CloudActionRequestAuthenticator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CloudActionRequestAuthenticatorTest {

    private static final Instant NOW = Instant.parse("2026-09-18T04:00:00Z");
    private static final String SECRET = "cloud-action-test-secret-that-is-long-enough";
    private static final String PATH = "/internal/rag-execution/actions";
    private static final String NONCE = "nonce-0123456789abcdef";

    private RagActionNonceStore nonceStore;
    private CloudActionRequestAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        nonceStore = mock(RagActionNonceStore.class);
        authenticator = new CloudActionRequestAuthenticator(
                new RagExecutionCloudActionProperties(
                        true, SECRET, Duration.ofSeconds(60), Duration.ofMinutes(3),
                        Duration.ofMinutes(2), 65_536
                ),
                nonceStore,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void acceptsBoundHmacAndConsumesNonceDurably() {
        SignedRequest request = signed("POST", PATH, NOW.getEpochSecond(), NONCE, "{}".getBytes(StandardCharsets.UTF_8));

        authenticate(request);

        verify(nonceStore).consume(
                CloudActionRequestAuthenticator.EXECUTION_CALLER,
                NONCE,
                NOW.plus(Duration.ofMinutes(3)),
                NOW
        );
    }

    @Test
    void rejectsAReplayReportedByTheDurableNonceStore() {
        doThrow(new DataIntegrityViolationException("duplicate nonce"))
                .when(nonceStore).consume(any(), any(), any(), any());
        SignedRequest request = signed("POST", PATH, NOW.getEpochSecond(), NONCE, new byte[0]);

        assertAuthenticationFailure(request, "replayed_service_signature");
    }

    @Test
    void rejectsBodyTamperingBeforeNonceConsumption() {
        SignedRequest signed = signed("POST", PATH, NOW.getEpochSecond(), NONCE, "{}".getBytes(StandardCharsets.UTF_8));
        SignedRequest tampered = new SignedRequest(
                signed.method(), signed.path(), signed.timestamp(), signed.nonce(), signed.bodyHash(),
                signed.signature(), "{\"changed\":true}".getBytes(StandardCharsets.UTF_8)
        );

        assertAuthenticationFailure(tampered, "content_digest_mismatch");
    }

    @Test
    void rejectsExpiredSignature() {
        SignedRequest request = signed(
                "POST", PATH, NOW.minus(Duration.ofSeconds(61)).getEpochSecond(), NONCE, new byte[0]
        );

        assertAuthenticationFailure(request, "expired_service_signature");
    }

    private SignedRequest signed(String method, String path, long epochSecond, String nonce, byte[] body) {
        String timestamp = Long.toString(epochSecond);
        String bodyHash = CloudActionRequestAuthenticator.sha256Hex(body);
        String canonical = CloudActionRequestAuthenticator.canonical(
                method,
                path,
                CloudActionRequestAuthenticator.EXECUTION_CALLER,
                timestamp,
                nonce,
                bodyHash
        );
        return new SignedRequest(
                method,
                path,
                timestamp,
                nonce,
                bodyHash,
                CloudActionRequestAuthenticator.hmacSha256Hex(SECRET, canonical),
                body
        );
    }

    private void authenticate(SignedRequest request) {
        authenticator.authenticate(
                request.method(), request.path(), CloudActionRequestAuthenticator.EXECUTION_CALLER,
                request.timestamp(), request.nonce(), request.bodyHash(), request.signature(), request.body()
        );
    }

    private void assertAuthenticationFailure(SignedRequest request, String code) {
        assertThatThrownBy(() -> authenticate(request))
                .isInstanceOfSatisfying(CloudActionAuthenticationException.class, exception -> {
                    assertThat(exception.statusCode()).isEqualTo(401);
                    assertThat(exception.errorCode()).isEqualTo(code);
                });
    }

    private record SignedRequest(
            String method,
            String path,
            String timestamp,
            String nonce,
            String bodyHash,
            String signature,
            byte[] body
    ) {
    }
}
