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
    void createTokenEmbedsUserIdAndTokenVersionInLegacyCompatiblePayload() {
        IdentityUser user = newIdentityUser();
        ReflectionTestUtils.setField(user, "id", 33L);
        ReflectionTestUtils.setField(user, "phoneNumber", "13800000033");
        ReflectionTestUtils.setField(user, "tokenVersion", 7L);
        IdentityTokenService tokenService = new IdentityTokenService("test-secret", 3600L);

        String token = tokenService.createToken(user);
        String encodedPayload = token.substring(0, token.indexOf('.'));
        String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);

        assertThat(payload).startsWith("33:13800000033:7:");
    }

    @Test
    void parseTokenReadsSignedClaims() {
        IdentityUser user = newIdentityUser();
        ReflectionTestUtils.setField(user, "id", 33L);
        ReflectionTestUtils.setField(user, "phoneNumber", "13800000033");
        ReflectionTestUtils.setField(user, "tokenVersion", 7L);
        IdentityTokenService tokenService = new IdentityTokenService("test-secret", 3600L);

        IdentityTokenService.TokenClaims claims = tokenService.parseToken(tokenService.createToken(user));

        assertThat(claims.userId()).isEqualTo(33L);
        assertThat(claims.tokenVersion()).isEqualTo(7L);
        assertThat(claims.expiresAt()).isGreaterThan(Instant.now().getEpochSecond());
    }

    @Test
    void parseTokenSupportsLegacyThreePartPayload() throws Exception {
        IdentityTokenService tokenService = new IdentityTokenService("legacy-secret", 3600L);
        long expiresAt = Instant.now().getEpochSecond() + 3600L;
        String rawPayload = "44:13800000044:" + expiresAt;
        String encodedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawPayload.getBytes(StandardCharsets.UTF_8));
        String token = encodedPayload + "." + sign(encodedPayload, "legacy-secret");

        IdentityTokenService.TokenClaims claims = tokenService.parseToken(token);

        assertThat(claims.userId()).isEqualTo(44L);
        assertThat(claims.tokenVersion()).isZero();
        assertThat(claims.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void parseTokenRejectsInvalidSignature() {
        IdentityTokenService tokenService = new IdentityTokenService("test-secret", 3600L);

        assertThatThrownBy(() -> tokenService.parseToken("payload.signature"))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("Token 签名校验失败。");
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

    private String sign(String value, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
