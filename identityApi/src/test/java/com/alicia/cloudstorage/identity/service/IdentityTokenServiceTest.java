package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

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

    private IdentityUser newIdentityUser() {
        try {
            var constructor = IdentityUser.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Failed to create IdentityUser test fixture.", ex);
        }
    }
}
