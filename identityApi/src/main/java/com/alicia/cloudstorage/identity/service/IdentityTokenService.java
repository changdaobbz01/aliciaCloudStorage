package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class IdentityTokenService {

    private static final String TOKEN_PAYLOAD_VERSION = "v2";
    private static final String SESSION_TOKEN_PAYLOAD_VERSION = "v3";
    private static final String JWT_ALGORITHM = "HS256";
    private static final String JWT_TYPE = "JWT";
    private static final String JWT_KEY_ID = "alicia-hs256-v1";
    private static final String JWT_ISSUER = "https://windwindwind-alicia.cn";
    private static final String JWT_AUDIENCE = "alicia-tools";

    private final String secret;
    private final long expireSeconds;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

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
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + expireSeconds;
        long tokenVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", JWT_ALGORITHM);
        header.put("typ", JWT_TYPE);
        header.put("kid", JWT_KEY_ID);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", JWT_ISSUER);
        payload.put("sub", String.valueOf(user.getId()));
        payload.put("aud", JWT_AUDIENCE);
        payload.put("iat", issuedAt);
        payload.put("exp", expiresAt);
        payload.put("ver", tokenVersion);
        if (refreshSessionId != null) {
            payload.put("sid", refreshSessionId);
        }

        String encodedHeader = base64UrlEncodeJson(header);
        String encodedPayload = base64UrlEncodeJson(payload);
        String signedValue = encodedHeader + "." + encodedPayload;

        return signedValue + "." + sign(signedValue);
    }

    public TokenClaims parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IdentityAuthException("Token 不能为空。");
        }

        String[] parts = token.split("\\.", -1);
        TokenClaims tokenClaims;
        if (parts.length == 3) {
            tokenClaims = parseJwtToken(parts);
        } else if (parts.length == 2) {
            tokenClaims = parseLegacyToken(parts);
        } else {
            throw new IdentityAuthException("Token 格式不正确。");
        }

        long expiresAt = tokenClaims.expiresAt();
        if (Instant.now().getEpochSecond() >= expiresAt) {
            throw new IdentityAuthException("登录状态已过期。");
        }

        return tokenClaims;
    }

    private TokenClaims parseJwtToken(String[] parts) {
        String encodedHeader = parts[0];
        String encodedPayload = parts[1];
        String signature = parts[2];
        String signedValue = encodedHeader + "." + encodedPayload;
        assertSignature(signedValue, signature);

        Map<String, Object> header = readJsonObject(encodedHeader);
        Object algorithm = header.get("alg");
        if (!JWT_ALGORITHM.equals(algorithm)) {
            throw new IdentityAuthException("Token 签名算法不正确。");
        }

        Map<String, Object> payload = readJsonObject(encodedPayload);
        if (!JWT_ISSUER.equals(payload.get("iss"))) {
            throw new IdentityAuthException("Token 签发方不正确。");
        }
        requireAudience(payload);

        Long userId = parseUserId(stringClaim(payload, "sub", "Token 用户编号不合法。"));
        long tokenVersion = longClaim(payload, "ver", "Token 版本号不合法。");
        Long refreshSessionId = optionalPositiveLongClaim(payload, "sid", "Token 会话编号不合法。");
        long expiresAt = longClaim(payload, "exp", "Token 过期时间不合法。");

        return new TokenClaims(userId, tokenVersion, refreshSessionId, expiresAt);
    }

    private TokenClaims parseLegacyToken(String[] parts) {
        String encodedPayload = parts[0];
        String signature = parts[1];
        assertSignature(encodedPayload, signature);

        String payload = base64UrlDecode(encodedPayload);
        String[] payloadParts = payload.split(":");
        return parsePayloadParts(payloadParts);
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

    private void assertSignature(String signedValue, String signature) {
        String expectedSignature = sign(signedValue);

        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IdentityAuthException("Token 签名校验失败。");
        }
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

    private String base64UrlEncodeJson(Map<String, Object> value) {
        try {
            return base64UrlEncode(jsonMapper.writeValueAsString(value));
        } catch (JacksonException ex) {
            throw new IllegalStateException("生成 Token 载荷失败。", ex);
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

    private Map<String, Object> readJsonObject(String encodedValue) {
        String json = base64UrlDecode(encodedValue);
        try {
            Object value = jsonMapper.readValue(json, Object.class);
            if (!(value instanceof Map<?, ?> rawMap)) {
                throw new IdentityAuthException("Token 载荷不正确。");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IdentityAuthException("Token 载荷不正确。");
                }
                result.put(key, entry.getValue());
            }

            return result;
        } catch (JacksonException | IllegalArgumentException ex) {
            throw new IdentityAuthException("Token 载荷不正确。");
        }
    }

    private void requireAudience(Map<String, Object> payload) {
        Object audience = payload.get("aud");
        if (JWT_AUDIENCE.equals(audience)) {
            return;
        }

        if (audience instanceof Iterable<?> values) {
            for (Object value : values) {
                if (Objects.equals(JWT_AUDIENCE, value)) {
                    return;
                }
            }
        }

        throw new IdentityAuthException("Token 受众不正确。");
    }

    private String stringClaim(Map<String, Object> payload, String name, String errorMessage) {
        Object value = payload.get(name);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }

        if (value instanceof Number number) {
            return String.valueOf(number.longValue());
        }

        throw new IdentityAuthException(errorMessage);
    }

    private long longClaim(Map<String, Object> payload, String name, String errorMessage) {
        Object value = payload.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof String text) {
            return parseLongClaim(text, errorMessage);
        }

        throw new IdentityAuthException(errorMessage);
    }

    private Long optionalPositiveLongClaim(Map<String, Object> payload, String name, String errorMessage) {
        if (!payload.containsKey(name)) {
            return null;
        }

        long value = longClaim(payload, name, errorMessage);
        if (value <= 0L) {
            throw new IdentityAuthException(errorMessage);
        }

        return value;
    }

    private long parseLongClaim(String value, String errorMessage) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IdentityAuthException(errorMessage);
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
