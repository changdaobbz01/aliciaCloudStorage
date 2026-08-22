package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityTokenServiceTest {

    @Test
    void createTokenUsesJwtPayloadWithoutPhoneNumber() {
        IdentityUser user = newIdentityUser();
        ReflectionTestUtils.setField(user, "id", 33L);
        ReflectionTestUtils.setField(user, "phoneNumber", "13800000033");
        ReflectionTestUtils.setField(user, "tokenVersion", 7L);
        IdentityTokenService tokenService = tokenService("test-secret", 3600L);

        String token = tokenService.createToken(user);
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        String header = decodePart(parts[0]);
        String payload = decodePart(parts[1]);

        assertThat(header).contains("\"alg\":\"HS256\"");
        assertThat(header).contains("\"typ\":\"JWT\"");
        assertThat(header).contains("\"kid\":\"alicia-hs256-v1\"");
        assertThat(payload).contains("\"iss\":\"https://windwindwind-alicia.cn\"");
        assertThat(payload).contains("\"sub\":\"33\"");
        assertThat(payload).contains("\"aud\":\"alicia-tools\"");
        assertThat(payload).contains("\"ver\":7");
        assertThat(payload).doesNotContain("13800000033");
    }

    @Test
    void createTokenUsesConfiguredJwtMetadata() {
        IdentityUser user = newIdentityUser();
        ReflectionTestUtils.setField(user, "id", 33L);
        ReflectionTestUtils.setField(user, "tokenVersion", 7L);
        IdentityTokenService tokenService = tokenService(
                "test-secret",
                3600L,
                "https://identity.example",
                "alicia-stage",
                "stage-key-1"
        );

        String token = tokenService.createToken(user);
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        String header = decodePart(parts[0]);
        String payload = decodePart(parts[1]);

        assertThat(header).contains("\"kid\":\"stage-key-1\"");
        assertThat(payload).contains("\"iss\":\"https://identity.example\"");
        assertThat(payload).contains("\"aud\":\"alicia-stage\"");
    }

    @Test
    void createTokenCanBindRefreshSessionIdInJwtPayload() {
        IdentityUser user = newIdentityUser();
        ReflectionTestUtils.setField(user, "id", 33L);
        ReflectionTestUtils.setField(user, "phoneNumber", "13800000033");
        ReflectionTestUtils.setField(user, "tokenVersion", 7L);
        IdentityTokenService tokenService = tokenService("test-secret", 3600L);

        String token = tokenService.createToken(user, 51L);
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        String payload = decodePart(parts[1]);

        assertThat(payload).contains("\"sub\":\"33\"");
        assertThat(payload).contains("\"ver\":7");
        assertThat(payload).contains("\"sid\":51");
        assertThat(payload).doesNotContain("13800000033");
    }

    @Test
    void parseTokenReadsSignedClaims() {
        IdentityUser user = newIdentityUser();
        ReflectionTestUtils.setField(user, "id", 33L);
        ReflectionTestUtils.setField(user, "phoneNumber", "13800000033");
        ReflectionTestUtils.setField(user, "tokenVersion", 7L);
        IdentityTokenService tokenService = tokenService("test-secret", 3600L);

        IdentityTokenService.TokenClaims claims = tokenService.parseToken(tokenService.createToken(user));

        assertThat(claims.userId()).isEqualTo(33L);
        assertThat(claims.tokenVersion()).isEqualTo(7L);
        assertThat(claims.refreshSessionId()).isNull();
        assertThat(claims.expiresAt()).isGreaterThan(Instant.now().getEpochSecond());
    }

    @Test
    void parseTokenReadsRefreshSessionIdFromJwtPayload() {
        IdentityUser user = newIdentityUser();
        ReflectionTestUtils.setField(user, "id", 33L);
        ReflectionTestUtils.setField(user, "tokenVersion", 7L);
        IdentityTokenService tokenService = tokenService("test-secret", 3600L);

        IdentityTokenService.TokenClaims claims = tokenService.parseToken(tokenService.createToken(user, 51L));

        assertThat(claims.userId()).isEqualTo(33L);
        assertThat(claims.tokenVersion()).isEqualTo(7L);
        assertThat(claims.refreshSessionId()).isEqualTo(51L);
        assertThat(claims.expiresAt()).isGreaterThan(Instant.now().getEpochSecond());
    }

    @Test
    void parseTokenSupportsLegacyVersionTwoPayload() throws Exception {
        IdentityTokenService tokenService = tokenService("legacy-secret", 3600L);
        long expiresAt = Instant.now().getEpochSecond() + 3600L;
        String token = legacyToken("v2:44:9:" + expiresAt, "legacy-secret");

        IdentityTokenService.TokenClaims claims = tokenService.parseToken(token);

        assertThat(claims.userId()).isEqualTo(44L);
        assertThat(claims.tokenVersion()).isEqualTo(9L);
        assertThat(claims.refreshSessionId()).isNull();
        assertThat(claims.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void parseTokenSupportsLegacyVersionThreePayload() throws Exception {
        IdentityTokenService tokenService = tokenService("legacy-secret", 3600L);
        long expiresAt = Instant.now().getEpochSecond() + 3600L;
        String token = legacyToken("v3:44:9:52:" + expiresAt, "legacy-secret");

        IdentityTokenService.TokenClaims claims = tokenService.parseToken(token);

        assertThat(claims.userId()).isEqualTo(44L);
        assertThat(claims.tokenVersion()).isEqualTo(9L);
        assertThat(claims.refreshSessionId()).isEqualTo(52L);
        assertThat(claims.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void parseTokenSupportsLegacyThreePartPayload() throws Exception {
        IdentityTokenService tokenService = tokenService("legacy-secret", 3600L);
        long expiresAt = Instant.now().getEpochSecond() + 3600L;
        String token = legacyToken("44:13800000044:" + expiresAt, "legacy-secret");

        IdentityTokenService.TokenClaims claims = tokenService.parseToken(token);

        assertThat(claims.userId()).isEqualTo(44L);
        assertThat(claims.tokenVersion()).isZero();
        assertThat(claims.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void parseTokenSupportsLegacyFourPartPayloadWithTokenVersion() throws Exception {
        IdentityTokenService tokenService = tokenService("legacy-secret", 3600L);
        long expiresAt = Instant.now().getEpochSecond() + 3600L;
        String token = legacyToken("44:13800000044:9:" + expiresAt, "legacy-secret");

        IdentityTokenService.TokenClaims claims = tokenService.parseToken(token);

        assertThat(claims.userId()).isEqualTo(44L);
        assertThat(claims.tokenVersion()).isEqualTo(9L);
        assertThat(claims.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void parseTokenRejectsInvalidSignature() {
        IdentityTokenService tokenService = tokenService("test-secret", 3600L);

        assertThatThrownBy(() -> tokenService.parseToken("payload.signature"))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("Token 签名校验失败。");
    }

    @Test
    void parseTokenRejectsJwtWithInvalidSignature() {
        IdentityUser user = newIdentityUser();
        ReflectionTestUtils.setField(user, "id", 33L);
        ReflectionTestUtils.setField(user, "tokenVersion", 7L);
        IdentityTokenService tokenService = tokenService("test-secret", 3600L);
        String token = tokenService.createToken(user);
        String tamperedToken = token.substring(0, token.lastIndexOf('.') + 1) + "tampered";

        assertThatThrownBy(() -> tokenService.parseToken(tamperedToken))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("Token 签名校验失败。");
    }

    @Test
    void parseTokenRejectsJwtWithUnexpectedIssuer() throws Exception {
        IdentityTokenService tokenService = tokenService("test-secret", 3600L);
        String token = jwtToken("https://other.example", "alicia-tools", "alicia-hs256-v1", "test-secret");

        assertThatThrownBy(() -> tokenService.parseToken(token))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("Token 签发方不正确。");
    }

    @Test
    void parseTokenRejectsJwtWithUnexpectedAudience() throws Exception {
        IdentityTokenService tokenService = tokenService("test-secret", 3600L);
        String token = jwtToken("https://windwindwind-alicia.cn", "other-audience", "alicia-hs256-v1", "test-secret");

        assertThatThrownBy(() -> tokenService.parseToken(token))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("Token 受众不正确。");
    }

    @Test
    void parseTokenRejectsJwtWithUnexpectedKeyId() throws Exception {
        IdentityTokenService tokenService = tokenService("test-secret", 3600L);
        String token = jwtToken("https://windwindwind-alicia.cn", "alicia-tools", "other-key", "test-secret");

        assertThatThrownBy(() -> tokenService.parseToken(token))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("Token 密钥标识不正确。");
    }

    @Test
    void constructorRejectsBlankJwtMetadata() {
        assertThatThrownBy(() -> tokenService("test-secret", 3600L, " ", "alicia-tools", "alicia-hs256-v1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Token issuer must not be blank.");

        assertThatThrownBy(() -> tokenService("test-secret", 3600L, "https://windwindwind-alicia.cn", " ", "alicia-hs256-v1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Token audience must not be blank.");

        assertThatThrownBy(() -> tokenService("test-secret", 3600L, "https://windwindwind-alicia.cn", "alicia-tools", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Token key id must not be blank.");
    }

    private IdentityUser newIdentityUser() {
        try {
            var constructor = IdentityUser.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Failed to create IdentityUser test fixture.", ex);
        }
    }

    private IdentityTokenService tokenService(String secret, long expireSeconds) {
        return tokenService(
                secret,
                expireSeconds,
                "https://windwindwind-alicia.cn",
                "alicia-tools",
                "alicia-hs256-v1"
        );
    }

    private IdentityTokenService tokenService(
            String secret,
            long expireSeconds,
            String issuer,
            String audience,
            String keyId
    ) {
        return new IdentityTokenService(secret, expireSeconds, issuer, audience, keyId);
    }

    private String legacyToken(String rawPayload, String secret) throws Exception {
        String encodedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawPayload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload, secret);
    }

    private String jwtToken(String issuer, String audience, String keyId, String secret) throws Exception {
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + 3600L;
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"" + keyId + "\"}";
        String payload = "{\"iss\":\"" + issuer + "\",\"sub\":\"44\",\"aud\":\"" + audience + "\",\"iat\":" + now
                + ",\"exp\":" + expiresAt + ",\"ver\":9}";
        String encodedHeader = encodePart(header);
        String encodedPayload = encodePart(payload);
        String signedValue = encodedHeader + "." + encodedPayload;
        return signedValue + "." + sign(signedValue, secret);
    }

    private String encodePart(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodePart(String encodedPayload) {
        return new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
    }

    private String sign(String value, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
