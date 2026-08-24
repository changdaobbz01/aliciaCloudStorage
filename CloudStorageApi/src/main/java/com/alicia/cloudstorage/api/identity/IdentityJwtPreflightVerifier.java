package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.principal.PrincipalAccessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class IdentityJwtPreflightVerifier implements IdentityAccessTokenPreflightVerifier {

    private static final String JWT_ALGORITHM_RS256 = "RS256";
    private static final String JWK_KEY_TYPE_RSA = "RSA";
    private static final String JWK_USE_SIGNATURE = "sig";

    private final RestClient restClient;
    private final JsonMapper objectMapper;
    private final IdentityTokenVerificationProperties properties;
    private volatile CachedJwks cachedJwks;

    public IdentityJwtPreflightVerifier(
            @Qualifier("identityRestClient") RestClient restClient,
            JsonMapper objectMapper,
            IdentityTokenVerificationProperties properties
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void verify(String authorization) {
        if (!properties.preflightEnabled()) {
            return;
        }

        String token = extractBearerToken(authorization);
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new PrincipalAccessException("Token 格式不正确。");
        }

        Map<String, Object> header = readJsonObject(parts[0]);
        String algorithm = stringClaim(header, "alg", "Token 签名算法不正确。");
        if (!JWT_ALGORITHM_RS256.equals(algorithm)) {
            return;
        }

        String keyId = stringClaim(header, "kid", "Token 密钥标识不正确。");
        RSAPublicKey verificationKey = resolveVerificationKey(keyId);
        String signedValue = parts[0] + "." + parts[1];
        assertRsaSignature(signedValue, parts[2], verificationKey);

        Map<String, Object> payload = readJsonObject(parts[1]);
        if (!properties.issuer().equals(payload.get("iss"))) {
            throw new PrincipalAccessException("Token 签发方不正确。");
        }
        requireAudience(payload);

        parsePositiveLongClaim(payload, "sub", "Token 用户编号不合法。");
        longClaim(payload, "ver", "Token 版本号不合法。");
        optionalPositiveLongClaim(payload, "sid", "Token 会话编号不合法。");
        long expiresAt = longClaim(payload, "exp", "Token 过期时间不合法。");
        if (Instant.now().getEpochSecond() >= expiresAt) {
            throw new PrincipalAccessException("登录状态已过期。");
        }
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new PrincipalAccessException("请先登录。");
        }

        String prefix = "Bearer ";
        if (!authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new PrincipalAccessException("登录凭证格式不正确。");
        }

        String token = authorization.substring(prefix.length()).trim();
        if (token.isBlank()) {
            throw new PrincipalAccessException("请先登录。");
        }

        return token;
    }

    private RSAPublicKey resolveVerificationKey(String keyId) {
        CachedJwks snapshot = cachedJwks;
        if (snapshot != null && snapshot.isFresh()) {
            RSAPublicKey cachedKey = snapshot.keys().get(keyId);
            if (cachedKey != null) {
                return cachedKey;
            }
        }

        CachedJwks refreshed = fetchJwks();
        cachedJwks = refreshed;
        RSAPublicKey refreshedKey = refreshed.keys().get(keyId);
        if (refreshedKey == null) {
            throw new PrincipalAccessException("Token 密钥标识不正确。");
        }

        return refreshedKey;
    }

    private CachedJwks fetchJwks() {
        IdentityJwksResponse response = IdentityGatewaySupport.exchange(() -> restClient.get()
                .uri("/api/identity/.well-known/jwks.json")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(IdentityJwksResponse.class), objectMapper);

        if (response == null || response.keys() == null) {
            throw new IdentityServiceUnavailableException("身份服务 JWKS 响应为空。");
        }

        Map<String, RSAPublicKey> keys = new LinkedHashMap<>();
        for (IdentityJwk key : response.keys()) {
            if (!isUsableRsaSigningKey(key)) {
                continue;
            }
            if (keys.containsKey(key.kid())) {
                throw new IdentityServiceUnavailableException("身份服务 JWKS 密钥标识重复。");
            }
            keys.put(key.kid(), parseRsaPublicKey(key));
        }

        return new CachedJwks(
                Map.copyOf(keys),
                Instant.now().plus(properties.jwksCacheTtl())
        );
    }

    private boolean isUsableRsaSigningKey(IdentityJwk key) {
        return key != null
                && JWK_KEY_TYPE_RSA.equals(key.kty())
                && JWT_ALGORITHM_RS256.equals(key.alg())
                && (key.use() == null || key.use().isBlank() || JWK_USE_SIGNATURE.equals(key.use()))
                && hasText(key.kid())
                && hasText(key.n())
                && hasText(key.e());
    }

    private RSAPublicKey parseRsaPublicKey(IdentityJwk key) {
        try {
            BigInteger modulus = decodeUnsignedBigInteger(key.n());
            BigInteger publicExponent = decodeUnsignedBigInteger(key.e());
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(modulus, publicExponent));
        } catch (Exception ex) {
            throw new IdentityServiceUnavailableException("身份服务 JWKS RSA 公钥格式异常。", ex);
        }
    }

    private BigInteger decodeUnsignedBigInteger(String value) {
        try {
            return new BigInteger(1, Base64.getUrlDecoder().decode(value));
        } catch (IllegalArgumentException ex) {
            throw new IdentityServiceUnavailableException("身份服务 JWKS RSA 公钥格式异常。", ex);
        }
    }

    private void assertRsaSignature(String signedValue, String signature, RSAPublicKey verificationKey) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(verificationKey);
            verifier.update(signedValue.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getUrlDecoder().decode(signature);
            if (!verifier.verify(signatureBytes)) {
                throw new PrincipalAccessException("Token 签名校验失败。");
            }
        } catch (IllegalArgumentException ex) {
            throw new PrincipalAccessException("Token 签名校验失败。");
        } catch (PrincipalAccessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PrincipalAccessException("Token 签名校验失败。");
        }
    }

    private Map<String, Object> readJsonObject(String encodedValue) {
        String json = base64UrlDecode(encodedValue);
        try {
            Object value = objectMapper.readValue(json, Object.class);
            if (!(value instanceof Map<?, ?> rawMap)) {
                throw new PrincipalAccessException("Token 载荷不正确。");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new PrincipalAccessException("Token 载荷不正确。");
                }
                result.put(key, entry.getValue());
            }

            return result;
        } catch (JacksonException | IllegalArgumentException ex) {
            throw new PrincipalAccessException("Token 载荷不正确。");
        }
    }

    private String base64UrlDecode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new PrincipalAccessException("Token 载荷不正确。");
        }
    }

    private void requireAudience(Map<String, Object> payload) {
        Object audience = payload.get("aud");
        if (properties.audience().equals(audience)) {
            return;
        }

        if (audience instanceof Iterable<?> values) {
            for (Object value : values) {
                if (Objects.equals(properties.audience(), value)) {
                    return;
                }
            }
        }

        throw new PrincipalAccessException("Token 受众不正确。");
    }

    private String stringClaim(Map<String, Object> payload, String name, String errorMessage) {
        Object value = payload.get(name);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }

        if (value instanceof Number number) {
            return String.valueOf(number.longValue());
        }

        throw new PrincipalAccessException(errorMessage);
    }

    private long longClaim(Map<String, Object> payload, String name, String errorMessage) {
        Object value = payload.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof String text) {
            return parseLong(text, errorMessage);
        }

        throw new PrincipalAccessException(errorMessage);
    }

    private Long optionalPositiveLongClaim(Map<String, Object> payload, String name, String errorMessage) {
        if (!payload.containsKey(name)) {
            return null;
        }

        long value = longClaim(payload, name, errorMessage);
        if (value <= 0L) {
            throw new PrincipalAccessException(errorMessage);
        }

        return value;
    }

    private long parsePositiveLongClaim(Map<String, Object> payload, String name, String errorMessage) {
        long value = parseLong(stringClaim(payload, name, errorMessage), errorMessage);
        if (value <= 0L) {
            throw new PrincipalAccessException(errorMessage);
        }

        return value;
    }

    private long parseLong(String value, String errorMessage) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new PrincipalAccessException(errorMessage);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record IdentityJwksResponse(
            List<IdentityJwk> keys
    ) {
    }

    private record IdentityJwk(
            String kty,
            String use,
            String kid,
            String alg,
            String n,
            String e
    ) {
    }

    private record CachedJwks(
            Map<String, RSAPublicKey> keys,
            Instant expiresAt
    ) {
        boolean isFresh() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
