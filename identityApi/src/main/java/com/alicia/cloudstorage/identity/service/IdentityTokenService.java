package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Service
public class IdentityTokenService {

    private static final String TOKEN_PAYLOAD_VERSION = "v2";
    private static final String SESSION_TOKEN_PAYLOAD_VERSION = "v3";

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
        return createToken(user, null);
    }

    public String createToken(IdentityUser user, Long refreshSessionId) {
        long expiresAt = Instant.now().getEpochSecond() + expireSeconds;
        long tokenVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
        String payload = refreshSessionId == null
                ? TOKEN_PAYLOAD_VERSION + ":" + user.getId() + ":" + tokenVersion + ":" + expiresAt
                : SESSION_TOKEN_PAYLOAD_VERSION + ":" + user.getId() + ":" + tokenVersion + ":" + refreshSessionId + ":" + expiresAt;
        String encodedPayload = base64UrlEncode(payload);

        return encodedPayload + "." + sign(encodedPayload);
    }

    public TokenClaims parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IdentityAuthException("Token 不能为空。");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new IdentityAuthException("Token 格式不正确。");
        }

        String encodedPayload = parts[0];
        String signature = parts[1];
        String expectedSignature = sign(encodedPayload);

        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IdentityAuthException("Token 签名校验失败。");
        }

        String payload = base64UrlDecode(encodedPayload);
        String[] payloadParts = payload.split(":");
        TokenClaims tokenClaims = parsePayloadParts(payloadParts);
        long expiresAt = tokenClaims.expiresAt();
        if (Instant.now().getEpochSecond() >= expiresAt) {
            throw new IdentityAuthException("登录状态已过期。");
        }

        return tokenClaims;
    }

    private TokenClaims parsePayloadParts(String[] payloadParts) {
        if (payloadParts.length == 5 && SESSION_TOKEN_PAYLOAD_VERSION.equals(payloadParts[0])) {
            return new TokenClaims(
                    parseUserId(payloadParts[1]),
                    parseTokenVersion(payloadParts[2]),
                    parseRefreshSessionId(payloadParts[3]),
                    parseExpiresAt(payloadParts[4])
            );
        }

        if (payloadParts.length == 4 && TOKEN_PAYLOAD_VERSION.equals(payloadParts[0])) {
            return new TokenClaims(
                    parseUserId(payloadParts[1]),
                    parseTokenVersion(payloadParts[2]),
                    null,
                    parseExpiresAt(payloadParts[3])
            );
        }

        if (payloadParts.length == 4) {
            return new TokenClaims(
                    parseUserId(payloadParts[0]),
                    parseTokenVersion(payloadParts[2]),
                    null,
                    parseExpiresAt(payloadParts[3])
            );
        }

        if (payloadParts.length == 3) {
            return new TokenClaims(
                    parseUserId(payloadParts[0]),
                    0L,
                    null,
                    parseExpiresAt(payloadParts[2])
            );
        }

        throw new IdentityAuthException("Token 载荷不正确。");
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

    private String base64UrlDecode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IdentityAuthException("Token 载荷不正确。");
        }
    }

    private Long parseUserId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IdentityAuthException("Token 用户编号不合法。");
        }
    }

    private long parseTokenVersion(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IdentityAuthException("Token 版本号不合法。");
        }
    }

    private Long parseRefreshSessionId(String value) {
        try {
            long sessionId = Long.parseLong(value);
            if (sessionId <= 0L) {
                throw new NumberFormatException("session id must be positive");
            }
            return sessionId;
        } catch (NumberFormatException ex) {
            throw new IdentityAuthException("Token 会话编号不合法。");
        }
    }

    private long parseExpiresAt(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IdentityAuthException("Token 过期时间不合法。");
        }
    }

    public record TokenClaims(
            Long userId,
            long tokenVersion,
            Long refreshSessionId,
            long expiresAt
    ) {
        public TokenClaims(Long userId, long tokenVersion, long expiresAt) {
            this(userId, tokenVersion, null, expiresAt);
        }
    }
}
