package com.alicia.cloudstorage.api.auth;

import com.alicia.cloudstorage.api.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {

    @Test
    void createTokenEmbedsTokenVersion() {
        TokenService tokenService = new TokenService("test-secret", 3600L);
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", 33L);
        user.setPhoneNumber("13800000033");
        user.setTokenVersion(7L);

        TokenService.TokenClaims claims = tokenService.parseToken(tokenService.createToken(user));

        assertThat(claims.userId()).isEqualTo(33L);
        assertThat(claims.tokenVersion()).isEqualTo(7L);
        assertThat(claims.expiresAt()).isGreaterThan(Instant.now().getEpochSecond());
    }

    @Test
    void parseTokenSupportsLegacyThreePartPayload() throws Exception {
        TokenService tokenService = new TokenService("legacy-secret", 3600L);
        long expiresAt = Instant.now().getEpochSecond() + 3600L;
        String rawPayload = "44:13800000044:" + expiresAt;
        String encodedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawPayload.getBytes(StandardCharsets.UTF_8));
        String token = encodedPayload + "." + sign(encodedPayload, "legacy-secret");

        TokenService.TokenClaims claims = tokenService.parseToken(token);

        assertThat(claims.userId()).isEqualTo(44L);
        assertThat(claims.tokenVersion()).isZero();
        assertThat(claims.expiresAt()).isEqualTo(expiresAt);
    }

    private String sign(String value, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
