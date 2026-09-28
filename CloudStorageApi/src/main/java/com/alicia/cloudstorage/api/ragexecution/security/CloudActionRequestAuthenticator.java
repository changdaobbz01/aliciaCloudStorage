package com.alicia.cloudstorage.api.ragexecution.security;

import com.alicia.cloudstorage.api.ragexecution.config.RagExecutionCloudActionProperties;
import com.alicia.cloudstorage.api.ragexecution.persistence.RagActionNonceStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class CloudActionRequestAuthenticator {

    public static final String CALLER_HEADER = "X-Alicia-Caller";
    public static final String TIMESTAMP_HEADER = "X-Alicia-Timestamp";
    public static final String NONCE_HEADER = "X-Alicia-Nonce";
    public static final String BODY_SHA256_HEADER = "X-Alicia-Content-SHA256";
    public static final String SIGNATURE_HEADER = "X-Alicia-Signature";
    public static final String EXECUTION_CALLER = "rag-execution";

    private final RagExecutionCloudActionProperties properties;
    private final RagActionNonceStore nonceStore;
    private final Clock clock;

    public CloudActionRequestAuthenticator(
            RagExecutionCloudActionProperties properties,
            RagActionNonceStore nonceStore,
            Clock clock
    ) {
        this.properties = properties;
        this.nonceStore = nonceStore;
        this.clock = clock;
    }

    public void authenticate(
            String method,
            String path,
            String caller,
            String timestamp,
            String nonce,
            String bodyHash,
            String suppliedSignature,
            byte[] body
    ) {
        requireExact(caller, EXECUTION_CALLER, "invalid_service_identity");
        requirePattern(timestamp, "-?[0-9]{1,19}", "invalid_service_timestamp");
        requirePattern(nonce, "[A-Za-z0-9._:-]{16,64}", "invalid_service_nonce");
        requirePattern(bodyHash, "[0-9a-f]{64}", "invalid_content_digest");
        requirePattern(suppliedSignature, "[0-9a-f]{64}", "invalid_service_signature");

        Instant now = clock.instant();
        Instant issuedAt;
        try {
            issuedAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
        } catch (RuntimeException exception) {
            throw unauthorized("invalid_service_timestamp");
        }
        Duration skew = Duration.between(issuedAt, now).abs();
        if (skew.compareTo(properties.maximumClockSkew()) > 0) {
            throw unauthorized("expired_service_signature");
        }

        String actualBodyHash = sha256Hex(body);
        if (!constantTimeEquals(actualBodyHash, bodyHash)) {
            throw unauthorized("content_digest_mismatch");
        }
        String expected = hmacSha256Hex(
                properties.serviceSecret(),
                canonical(method, path, caller, timestamp, nonce, bodyHash)
        );
        if (!constantTimeEquals(expected, suppliedSignature)) {
            throw unauthorized("invalid_service_signature");
        }

        try {
            nonceStore.consume(caller, nonce, now.plus(properties.nonceTtl()), now);
        } catch (IllegalStateException | DataIntegrityViolationException exception) {
            throw unauthorized("replayed_service_signature");
        }
    }

    public static String canonical(
            String method,
            String path,
            String caller,
            String timestamp,
            String nonce,
            String bodyHash
    ) {
        return String.join("\n", method, path, caller, timestamp, nonce, bodyHash);
    }

    public static String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public static String hmacSha256Hex(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", exception);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static void requireExact(String actual, String expected, String errorCode) {
        if (!expected.equals(actual)) {
            throw unauthorized(errorCode);
        }
    }

    private static void requirePattern(String value, String pattern, String errorCode) {
        if (value == null || !value.matches(pattern)) {
            throw unauthorized(errorCode);
        }
    }

    private static CloudActionAuthenticationException unauthorized(String errorCode) {
        return new CloudActionAuthenticationException(401, errorCode);
    }
}
