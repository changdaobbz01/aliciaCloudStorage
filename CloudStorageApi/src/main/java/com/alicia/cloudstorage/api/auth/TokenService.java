package com.alicia.cloudstorage.api.auth;

import com.alicia.cloudstorage.api.entity.SysUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Service
public class TokenService {

    private final String secret;
    private final long expireSeconds;

    public TokenService(
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

    /**
     * 为当前登录用户生成带签名的访问令牌。
     */
    public String createToken(SysUser user) {
        long expiresAt = Instant.now().getEpochSecond() + expireSeconds;
        String payload = user.getId() + ":" + user.getPhoneNumber() + ":" + expiresAt;
        String encodedPayload = base64UrlEncode(payload);
        String signature = sign(encodedPayload);

        return encodedPayload + "." + signature;
    }

    /**
     * 解析并校验令牌内容，成功时返回用户编号。
     */
    public Long parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthException("Token 不能为空。");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new AuthException("Token 格式不正确。");
        }

        String encodedPayload = parts[0];
        String signature = parts[1];
        String expectedSignature = sign(encodedPayload);

        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new AuthException("Token 签名校验失败。");
        }

        String payload = base64UrlDecode(encodedPayload);
        String[] payloadParts = payload.split(":");
        if (payloadParts.length != 3) {
            throw new AuthException("Token 载荷不正确。");
        }

        long expiresAt = parseExpiresAt(payloadParts[2]);
        if (Instant.now().getEpochSecond() >= expiresAt) {
            throw new AuthException("登录状态已过期。");
        }

        return parseUserId(payloadParts[0]);
    }

    /**
     * 对令牌载荷执行 HmacSHA256 签名。
     */
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

    /**
     * 使用 URL 安全的 Base64 对字符串进行编码。
     */
    private String base64UrlEncode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 对 URL 安全的 Base64 字符串执行解码。
     */
    private String base64UrlDecode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new AuthException("Token 载荷不正确。");
        }
    }

    /**
     * 将令牌中的用户编号字段解析为长整型。
     */
    private Long parseUserId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new AuthException("Token 用户编号不合法。");
        }
    }

    /**
     * 将令牌中的过期时间字段解析为时间戳。
     */
    private long parseExpiresAt(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new AuthException("Token 过期时间不合法。");
        }
    }
}
