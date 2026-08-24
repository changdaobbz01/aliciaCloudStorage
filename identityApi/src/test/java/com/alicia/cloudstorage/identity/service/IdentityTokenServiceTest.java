package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

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
    void createTokenUsesCurrentKeyWhenPreviousKeysAreConfigured() {
        IdentityUser user = newIdentityUser();
        ReflectionTestUtils.setField(user, "id", 33L);
        ReflectionTestUtils.setField(user, "tokenVersion", 7L);
        IdentityTokenService tokenService = tokenService(
                "current-secret",
                3600L,
                "https://windwindwind-alicia.cn",
                "alicia-tools",
                "current-key",
                "old-key=old-secret"
        );

        String token = tokenService.createToken(user);
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        String header = decodePart(parts[0]);

        assertThat(header).contains("\"kid\":\"current-key\"");
        assertThat(tokenService.parseToken(token).userId()).isEqualTo(33L);
    }

    @Test
    void createTokenCanUseRs256AndExposePublicJwks() throws Exception {
        IdentityUser user = newIdentityUser();
        ReflectionTestUtils.setField(user, "id", 33L);
        ReflectionTestUtils.setField(user, "tokenVersion", 7L);
        RsaFixture rsa = rsaFixture();
        IdentityTokenService tokenService = rsaTokenService("alicia-rs256-v1", rsa);

        String token = tokenService.createToken(user, 51L);
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        String header = decodePart(parts[0]);
        String payload = decodePart(parts[1]);
        IdentityTokenService.TokenClaims claims = tokenService.parseToken(token);

        assertThat(header).contains("\"alg\":\"RS256\"");
        assertThat(header).contains("\"typ\":\"JWT\"");
        assertThat(header).contains("\"kid\":\"alicia-rs256-v1\"");
        assertThat(payload).contains("\"sid\":51");
        assertThat(claims.userId()).isEqualTo(33L);
        assertThat(claims.tokenVersion()).isEqualTo(7L);
        assertThat(claims.refreshSessionId()).isEqualTo(51L);

        Map<String, Object> jwks = tokenService.jwks();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
        assertThat(keys).hasSize(1);
        assertThat(keys.getFirst()).containsEntry("kty", "RSA")
                .containsEntry("use", "sig")
                .containsEntry("kid", "alicia-rs256-v1")
                .containsEntry("alg", "RS256");
        assertThat(keys.getFirst()).containsKeys("n", "e");
        assertThat(keys.getFirst()).doesNotContainKeys("d", "p", "q");
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
    void parseTokenRejectsLegacyVersionTwoPayload() throws Exception {
        IdentityTokenService tokenService = tokenService("legacy-secret", 3600L);
        long expiresAt = Instant.now().getEpochSecond() + 3600L;
        String token = legacyToken("v2:44:9:" + expiresAt, "legacy-secret");

        assertThatThrownBy(() -> tokenService.parseToken(token))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("Token 格式不正确。");
    }

    @Test
    void parseTokenRejectsLegacyVersionThreePayload() throws Exception {
        IdentityTokenService tokenService = tokenService("legacy-secret", 3600L);
        long expiresAt = Instant.now().getEpochSecond() + 3600L;
        String token = legacyToken("v3:44:9:52:" + expiresAt, "legacy-secret");

        assertThatThrownBy(() -> tokenService.parseToken(token))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("Token 格式不正确。");
    }

    @Test
    void parseTokenRejectsInvalidSignature() {
        IdentityTokenService tokenService = tokenService("test-secret", 3600L);

        assertThatThrownBy(() -> tokenService.parseToken("payload.signature"))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("Token 格式不正确。");
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
    void parseTokenAcceptsJwtSignedWithPreviousKey() throws Exception {
        IdentityTokenService tokenService = tokenService(
                "current-secret",
                3600L,
                "https://windwindwind-alicia.cn",
                "alicia-tools",
                "current-key",
                "old-key=old-secret"
        );
        String token = jwtToken("https://windwindwind-alicia.cn", "alicia-tools", "old-key", "old-secret");

        IdentityTokenService.TokenClaims claims = tokenService.parseToken(token);

        assertThat(claims.userId()).isEqualTo(44L);
        assertThat(claims.tokenVersion()).isEqualTo(9L);
    }

    @Test
    void parseTokenAcceptsRs256JwtSignedWithPreviousPublicKey() throws Exception {
        RsaFixture current = rsaFixture();
        RsaFixture previous = rsaFixture();
        IdentityTokenService tokenService = new IdentityTokenService(
                "legacy-secret",
                3600L,
                "https://windwindwind-alicia.cn",
                "alicia-tools",
                "current-rsa",
                "old-hmac=old-secret",
                "RS256",
                current.privateKeyPem(),
                current.publicKeyPem(),
                "old-rsa=" + previous.publicKeyPem()
        );
        String token = rs256JwtToken(
                "https://windwindwind-alicia.cn",
                "alicia-tools",
                "old-rsa",
                previous.privateKey()
        );

        IdentityTokenService.TokenClaims claims = tokenService.parseToken(token);

        assertThat(claims.userId()).isEqualTo(44L);
        assertThat(claims.tokenVersion()).isEqualTo(9L);
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

    @Test
    void constructorRejectsUnsupportedTokenAlgorithm() {
        assertThatThrownBy(() -> new IdentityTokenService(
                "test-secret",
                3600L,
                "https://windwindwind-alicia.cn",
                "alicia-tools",
                "current-key",
                "",
                "ES256",
                "",
                "",
                ""
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Token algorithm must be HS256 or RS256.");
    }

    @Test
    void constructorRejectsRs256WithoutKeyPair() {
        assertThatThrownBy(() -> new IdentityTokenService(
                "test-secret",
                3600L,
                "https://windwindwind-alicia.cn",
                "alicia-tools",
                "current-rsa",
                "",
                "RS256",
                "",
                "",
                ""
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("RSA private key must not be blank when RS256 is enabled.");
    }

    @Test
    void constructorRejectsInvalidPreviousKeys() {
        assertThatThrownBy(() -> tokenService(
                "test-secret",
                3600L,
                "https://windwindwind-alicia.cn",
                "alicia-tools",
                "current-key",
                "missing-separator"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Previous token keys must use kid=secret entries separated by semicolons.");

        assertThatThrownBy(() -> tokenService(
                "test-secret",
                3600L,
                "https://windwindwind-alicia.cn",
                "alicia-tools",
                "current-key",
                "current-key=old-secret"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Token key id must be unique.");
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
        return tokenService(secret, expireSeconds, issuer, audience, keyId, "");
    }

    private IdentityTokenService tokenService(
            String secret,
            long expireSeconds,
            String issuer,
            String audience,
            String keyId,
            String previousKeys
    ) {
        return new IdentityTokenService(secret, expireSeconds, issuer, audience, keyId, previousKeys, "HS256", "", "", "");
    }

    private IdentityTokenService rsaTokenService(String keyId, RsaFixture rsa) {
        return new IdentityTokenService(
                "legacy-secret",
                3600L,
                "https://windwindwind-alicia.cn",
                "alicia-tools",
                keyId,
                "legacy-hmac=old-secret",
                "RS256",
                rsa.privateKeyPem(),
                rsa.publicKeyPem(),
                ""
        );
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

    private String rs256JwtToken(String issuer, String audience, String keyId, PrivateKey privateKey) throws Exception {
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + 3600L;
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + keyId + "\"}";
        String payload = "{\"iss\":\"" + issuer + "\",\"sub\":\"44\",\"aud\":\"" + audience + "\",\"iat\":" + now
                + ",\"exp\":" + expiresAt + ",\"ver\":9}";
        String encodedHeader = encodePart(header);
        String encodedPayload = encodePart(payload);
        String signedValue = encodedHeader + "." + encodedPayload;
        return signedValue + "." + signRsa(signedValue, privateKey);
    }

    private RsaFixture rsaFixture() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return new RsaFixture(
                keyPair.getPrivate(),
                pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()),
                pem("PUBLIC KEY", keyPair.getPublic().getEncoded())
        );
    }

    private String pem(String type, byte[] value) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(value);
        return "-----BEGIN " + type + "-----\n" + encoded + "\n-----END " + type + "-----";
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

    private String signRsa(String value, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private record RsaFixture(
            PrivateKey privateKey,
            String privateKeyPem,
            String publicKeyPem
    ) {
    }
}
