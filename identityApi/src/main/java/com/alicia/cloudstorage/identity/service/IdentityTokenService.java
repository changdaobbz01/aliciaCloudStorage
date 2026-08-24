package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class IdentityTokenService {

    private static final String JWT_ALGORITHM_HS256 = "HS256";
    private static final String JWT_ALGORITHM_RS256 = "RS256";
    private static final String JWT_TYPE = "JWT";

    private final String secret;
    private final long expireSeconds;
    private final String issuer;
    private final String audience;
    private final String keyId;
    private final String tokenAlgorithm;
    private final Map<String, String> secretsByKeyId;
    private final PrivateKey rsaPrivateKey;
    private final Map<String, RSAPublicKey> rsaPublicKeysByKeyId;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public IdentityTokenService(
            @Value("${alicia.auth.token-secret}") String secret,
            @Value("${alicia.auth.token-expire-seconds}") long expireSeconds,
            @Value("${alicia.auth.token-issuer}") String issuer,
            @Value("${alicia.auth.token-audience}") String audience,
            @Value("${alicia.auth.token-key-id}") String keyId,
            @Value("${alicia.auth.token-previous-keys:}") String previousKeys,
            @Value("${alicia.auth.token-algorithm:HS256}") String tokenAlgorithm,
            @Value("${alicia.auth.token-rsa-private-key:}") String rsaPrivateKey,
            @Value("${alicia.auth.token-rsa-public-key:}") String rsaPublicKey,
            @Value("${alicia.auth.token-previous-rsa-public-keys:}") String previousRsaPublicKeys
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Token secret must not be blank.");
        }

        if (expireSeconds <= 0) {
            throw new IllegalStateException("Token expiration must be greater than zero.");
        }

        this.secret = secret;
        this.expireSeconds = expireSeconds;
        this.issuer = requireText(issuer, "Token issuer must not be blank.");
        this.audience = requireText(audience, "Token audience must not be blank.");
        this.keyId = requireText(keyId, "Token key id must not be blank.");
        this.tokenAlgorithm = normalizeTokenAlgorithm(tokenAlgorithm);
        this.secretsByKeyId = buildSecretsByKeyId(this.tokenAlgorithm, this.keyId, this.secret, previousKeys);
        this.rsaPrivateKey = JWT_ALGORITHM_RS256.equals(this.tokenAlgorithm)
                ? parseRsaPrivateKey(requireText(rsaPrivateKey, "RSA private key must not be blank when RS256 is enabled."))
                : null;
        this.rsaPublicKeysByKeyId = buildRsaPublicKeysByKeyId(
                this.tokenAlgorithm,
                this.keyId,
                rsaPublicKey,
                previousRsaPublicKeys
        );
        assertNoOverlappingTokenKeyIds(this.secretsByKeyId.keySet(), this.rsaPublicKeysByKeyId.keySet());
    }

    public Map<String, Object> jwks() {
        List<Map<String, Object>> keys = rsaPublicKeysByKeyId.entrySet().stream()
                .map(entry -> rsaJwk(entry.getKey(), entry.getValue()))
                .toList();

        return Map.of("keys", keys);
    }

    public String createToken(IdentityUser user) {
        return createToken(user, null);
    }

    public String createToken(IdentityUser user, Long refreshSessionId) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + expireSeconds;
        long tokenVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", tokenAlgorithm);
        header.put("typ", JWT_TYPE);
        header.put("kid", keyId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("sub", String.valueOf(user.getId()));
        payload.put("aud", audience);
        payload.put("iat", issuedAt);
        payload.put("exp", expiresAt);
        payload.put("ver", tokenVersion);
        if (refreshSessionId != null) {
            payload.put("sid", refreshSessionId);
        }

        String encodedHeader = base64UrlEncodeJson(header);
        String encodedPayload = base64UrlEncodeJson(payload);
        String signedValue = encodedHeader + "." + encodedPayload;

        return signedValue + "." + signCurrentJwt(signedValue);
    }

    public TokenClaims parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IdentityAuthException("Token 不能为空。");
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new IdentityAuthException("Token 格式不正确。");
        }
        TokenClaims tokenClaims = parseJwtToken(parts);

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

        Map<String, Object> header = readJsonObject(encodedHeader);
        Object algorithm = header.get("alg");
        String tokenKeyId = stringClaim(header, "kid", "Token 密钥标识不正确。");
        assertJwtSignature(signedValue, signature, algorithm, tokenKeyId);

        Map<String, Object> payload = readJsonObject(encodedPayload);
        if (!issuer.equals(payload.get("iss"))) {
            throw new IdentityAuthException("Token 签发方不正确。");
        }
        requireAudience(payload);

        Long userId = parseUserId(stringClaim(payload, "sub", "Token 用户编号不合法。"));
        long tokenVersion = longClaim(payload, "ver", "Token 版本号不合法。");
        Long refreshSessionId = optionalPositiveLongClaim(payload, "sid", "Token 会话编号不合法。");
        long expiresAt = longClaim(payload, "exp", "Token 过期时间不合法。");

        return new TokenClaims(userId, tokenVersion, refreshSessionId, expiresAt);
    }

    private void assertSignature(String signedValue, String signature, String verificationSecret) {
        if (!signatureMatches(signedValue, signature, verificationSecret)) {
            throw new IdentityAuthException("Token 签名校验失败。");
        }
    }

    private void assertJwtSignature(String signedValue, String signature, Object algorithm, String tokenKeyId) {
        if (JWT_ALGORITHM_HS256.equals(algorithm)) {
            String verificationSecret = secretsByKeyId.get(tokenKeyId);
            if (verificationSecret == null) {
                throw new IdentityAuthException("Token 密钥标识不正确。");
            }
            assertSignature(signedValue, signature, verificationSecret);
            return;
        }

        if (JWT_ALGORITHM_RS256.equals(algorithm)) {
            RSAPublicKey verificationKey = rsaPublicKeysByKeyId.get(tokenKeyId);
            if (verificationKey == null) {
                throw new IdentityAuthException("Token 密钥标识不正确。");
            }
            assertRsaSignature(signedValue, signature, verificationKey);
            return;
        }

        throw new IdentityAuthException("Token 签名算法不正确。");
    }

    private boolean signatureMatches(String signedValue, String signature, String verificationSecret) {
        String expectedSignature = signHmac(signedValue, verificationSecret);

        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String signCurrentJwt(String value) {
        if (JWT_ALGORITHM_RS256.equals(tokenAlgorithm)) {
            return signRsa(value, rsaPrivateKey);
        }

        return signHmac(value, secret);
    }

    private String signHmac(String value, String signingSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("生成 Token 签名失败。", ex);
        }
    }

    private String signRsa(String value, PrivateKey signingKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(signingKey);
            signature.update(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("生成 Token 签名失败。", ex);
        }
    }

    private void assertRsaSignature(String signedValue, String signature, RSAPublicKey verificationKey) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(verificationKey);
            verifier.update(signedValue.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getUrlDecoder().decode(signature);
            if (!verifier.verify(signatureBytes)) {
                throw new IdentityAuthException("Token 签名校验失败。");
            }
        } catch (IllegalArgumentException ex) {
            throw new IdentityAuthException("Token 签名校验失败。");
        } catch (IdentityAuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IdentityAuthException("Token 签名校验失败。");
        }
    }

    private String requireText(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(errorMessage);
        }

        return value.trim();
    }

    private String normalizeTokenAlgorithm(String value) {
        String normalized = requireText(value, "Token algorithm must not be blank.").toUpperCase(Locale.ROOT);
        if (JWT_ALGORITHM_HS256.equals(normalized) || JWT_ALGORITHM_RS256.equals(normalized)) {
            return normalized;
        }

        throw new IllegalStateException("Token algorithm must be HS256 or RS256.");
    }

    private Map<String, String> buildSecretsByKeyId(
            String algorithm,
            String currentKeyId,
            String currentSecret,
            String previousKeys
    ) {
        Map<String, String> result = new LinkedHashMap<>();
        if (JWT_ALGORITHM_HS256.equals(algorithm)) {
            result.put(currentKeyId, currentSecret);
        }

        if (previousKeys == null || previousKeys.isBlank()) {
            return Collections.unmodifiableMap(result);
        }

        for (String rawEntry : previousKeys.split(";")) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                continue;
            }

            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                throw new IllegalStateException("Previous token keys must use kid=secret entries separated by semicolons.");
            }

            String previousKeyId = requireText(entry.substring(0, separator), "Previous token key id must not be blank.");
            String previousSecret = requireText(entry.substring(separator + 1), "Previous token secret must not be blank.");
            if (result.containsKey(previousKeyId)) {
                throw new IllegalStateException("Token key id must be unique.");
            }

            result.put(previousKeyId, previousSecret);
        }

        return Collections.unmodifiableMap(result);
    }

    private Map<String, RSAPublicKey> buildRsaPublicKeysByKeyId(
            String algorithm,
            String currentKeyId,
            String currentPublicKey,
            String previousPublicKeys
    ) {
        Map<String, RSAPublicKey> result = new LinkedHashMap<>();
        if (JWT_ALGORITHM_RS256.equals(algorithm)) {
            result.put(
                    currentKeyId,
                    parseRsaPublicKey(requireText(currentPublicKey, "RSA public key must not be blank when RS256 is enabled."))
            );
        }

        if (previousPublicKeys == null || previousPublicKeys.isBlank()) {
            return Collections.unmodifiableMap(result);
        }

        for (String rawEntry : previousPublicKeys.split(";")) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                continue;
            }

            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                throw new IllegalStateException("Previous RSA public keys must use kid=public-key entries separated by semicolons.");
            }

            String previousKeyId = requireText(entry.substring(0, separator), "Previous RSA public key id must not be blank.");
            String previousPublicKey = requireText(entry.substring(separator + 1), "Previous RSA public key must not be blank.");
            if (result.containsKey(previousKeyId)) {
                throw new IllegalStateException("Token key id must be unique.");
            }

            result.put(previousKeyId, parseRsaPublicKey(previousPublicKey));
        }

        return Collections.unmodifiableMap(result);
    }

    private void assertNoOverlappingTokenKeyIds(Set<String> hmacKeyIds, Set<String> rsaKeyIds) {
        Set<String> overlap = new HashSet<>(hmacKeyIds);
        overlap.retainAll(rsaKeyIds);
        if (!overlap.isEmpty()) {
            throw new IllegalStateException("Token key id must be unique.");
        }
    }

    private PrivateKey parseRsaPrivateKey(String value) {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decodePemOrBase64(value)));
        } catch (Exception ex) {
            throw new IllegalStateException("RSA private key must be a PKCS#8 PEM or base64 DER value.", ex);
        }
    }

    private RSAPublicKey parseRsaPublicKey(String value) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decodePemOrBase64(value)));
        } catch (Exception ex) {
            throw new IllegalStateException("RSA public key must be an X.509 PEM or base64 DER value.", ex);
        }
    }

    private byte[] decodePemOrBase64(String value) {
        String normalized = value.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private Map<String, Object> rsaJwk(String jwkKeyId, RSAPublicKey publicKey) {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("kid", jwkKeyId);
        jwk.put("alg", JWT_ALGORITHM_RS256);
        jwk.put("n", base64UrlEncodeUnsigned(publicKey.getModulus()));
        jwk.put("e", base64UrlEncodeUnsigned(publicKey.getPublicExponent()));
        return jwk;
    }

    private String base64UrlEncodeUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
        if (this.audience.equals(audience)) {
            return;
        }

        if (audience instanceof Iterable<?> values) {
            for (Object value : values) {
                if (Objects.equals(this.audience, value)) {
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
