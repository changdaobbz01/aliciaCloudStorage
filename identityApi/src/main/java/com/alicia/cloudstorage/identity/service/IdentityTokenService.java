package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class IdentityTokenService {

    private final String secret;
    private final long expireSeconds;

    public IdentityTokenService(
            @Value("${alicia.auth.token-secret}") String secret,
            @Value("${alicia.auth.token-expire-seconds}") long expireSeconds
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Token secret must not be blank.");
        }

        if (expireSeconds <= 0) {
            throw new IllegalStateException("Token expiration must be greater than zero.");
        }

        this.secret = secret;
        this.expireSeconds = expireSeconds;
    }

    public String createToken(IdentityUser user) {
        long expiresAt = Instant.now().getEpochSecond() + expireSeconds;
        long tokenVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
        String payload = user.getId() + ":" + user.getPhoneNumber() + ":" + tokenVersion + ":" + expiresAt;
        String encodedPayload = base64UrlEncode(payload);

        return encodedPayload + "." + sign(encodedPayload);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("生成 Token 签名失败。", ex);
        }
    }

    private String base64UrlEncode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
