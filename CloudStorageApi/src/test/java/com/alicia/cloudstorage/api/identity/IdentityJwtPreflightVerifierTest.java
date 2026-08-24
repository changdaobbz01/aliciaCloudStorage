package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.principal.PrincipalAccessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IdentityJwtPreflightVerifierTest {

    private static final String ISSUER = "https://windwindwind-alicia.cn";
    private static final String AUDIENCE = "alicia-tools";
    private static final String KEY_ID = "alicia-rs256-test";

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void verifyAcceptsValidRs256TokenAndCachesJwks() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        TestVerifierContext context = newContext(300L);
        context.server().expect(requestTo("http://identity.test/api/identity/.well-known/jwks.json"))
                .andRespond(withSuccess(jwks((RSAPublicKey) keyPair.getPublic()), MediaType.APPLICATION_JSON));

        String token = rs256Token(keyPair, KEY_ID, ISSUER, AUDIENCE, Instant.now().plusSeconds(600).getEpochSecond());

        context.verifier().verify("Bearer " + token);
        context.verifier().verify("Bearer " + token);

        context.server().verify();
    }

    @Test
    void verifySkipsNonRs256TokenWithoutJwksLookup() {
        TestVerifierContext context = newContext(300L);
        String token = unsignedToken("HS256", "alicia-hs256-v1", ISSUER, AUDIENCE);

        context.verifier().verify("Bearer " + token);

        context.server().verify();
    }

    @Test
    void verifyRejectsInvalidRs256SignatureLocally() throws Exception {
        KeyPair signingKey = rsaKeyPair();
        KeyPair jwksKey = rsaKeyPair();
        TestVerifierContext context = newContext(300L);
        context.server().expect(requestTo("http://identity.test/api/identity/.well-known/jwks.json"))
                .andRespond(withSuccess(jwks((RSAPublicKey) jwksKey.getPublic()), MediaType.APPLICATION_JSON));

        String token = rs256Token(signingKey, KEY_ID, ISSUER, AUDIENCE, Instant.now().plusSeconds(600).getEpochSecond());

        assertThatThrownBy(() -> context.verifier().verify("Bearer " + token))
                .isInstanceOf(PrincipalAccessException.class)
                .hasMessage("Token 签名校验失败。");

        context.server().verify();
    }

    @Test
    void verifyRejectsWrongIssuerAfterLocalSignatureCheck() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        TestVerifierContext context = newContext(300L);
        context.server().expect(requestTo("http://identity.test/api/identity/.well-known/jwks.json"))
                .andRespond(withSuccess(jwks((RSAPublicKey) keyPair.getPublic()), MediaType.APPLICATION_JSON));

        String token = rs256Token(
                keyPair,
                KEY_ID,
                "https://wrong.example",
                AUDIENCE,
                Instant.now().plusSeconds(600).getEpochSecond()
        );

        assertThatThrownBy(() -> context.verifier().verify("Bearer " + token))
                .isInstanceOf(PrincipalAccessException.class)
                .hasMessage("Token 签发方不正确。");

        context.server().verify();
    }

    @Test
    void verifyRejectsExpiredRs256TokenAfterLocalSignatureCheck() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        TestVerifierContext context = newContext(300L);
        context.server().expect(requestTo("http://identity.test/api/identity/.well-known/jwks.json"))
                .andRespond(withSuccess(jwks((RSAPublicKey) keyPair.getPublic()), MediaType.APPLICATION_JSON));

        String token = rs256Token(keyPair, KEY_ID, ISSUER, AUDIENCE, Instant.now().minusSeconds(5).getEpochSecond());

        assertThatThrownBy(() -> context.verifier().verify("Bearer " + token))
                .isInstanceOf(PrincipalAccessException.class)
                .hasMessage("登录状态已过期。");

        context.server().verify();
    }

    @Test
    void verifyCanBeDisabled() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        TestVerifierContext context = newContext(false, 300L);
        String token = rs256Token(keyPair, KEY_ID, "https://wrong.example", AUDIENCE, 1L);

        context.verifier().verify("Bearer " + token);

        context.server().verify();
    }

    private TestVerifierContext newContext(long cacheSeconds) {
        return newContext(true, cacheSeconds);
    }

    private TestVerifierContext newContext(boolean enabled, long cacheSeconds) {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.baseUrl("http://identity.test").build();
        IdentityJwtPreflightVerifier verifier = new IdentityJwtPreflightVerifier(
                restClient,
                objectMapper,
                new IdentityTokenVerificationProperties(enabled, ISSUER, AUDIENCE, cacheSeconds),
                new IdentityGatewayTelemetry()
        );
        return new TestVerifierContext(server, verifier);
    }

    private KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String rs256Token(
            KeyPair keyPair,
            String keyId,
            String issuer,
            Object audience,
            long expiresAt
    ) throws Exception {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", keyId);

        String signedValue = base64UrlJson(header) + "." + base64UrlJson(payload(issuer, audience, expiresAt));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(signedValue.getBytes(StandardCharsets.UTF_8));
        return signedValue + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private String unsignedToken(String algorithm, String keyId, String issuer, Object audience) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", algorithm);
        header.put("typ", "JWT");
        header.put("kid", keyId);
        return base64UrlJson(header)
                + "."
                + base64UrlJson(payload(issuer, audience, Instant.now().plusSeconds(600).getEpochSecond()))
                + ".signature";
    }

    private Map<String, Object> payload(String issuer, Object audience, long expiresAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("sub", "1");
        payload.put("aud", audience);
        payload.put("iat", Instant.now().getEpochSecond());
        payload.put("exp", expiresAt);
        payload.put("ver", 0);
        payload.put("sid", 12);
        return payload;
    }

    private String jwks(RSAPublicKey publicKey) {
        return """
                {
                  "keys": [
                    {
                      "kty": "RSA",
                      "use": "sig",
                      "kid": "%s",
                      "alg": "RS256",
                      "n": "%s",
                      "e": "%s"
                    }
                  ]
                }
                """.formatted(
                KEY_ID,
                base64UrlUnsigned(publicKey.getModulus()),
                base64UrlUnsigned(publicKey.getPublicExponent())
        );
    }

    private String base64UrlJson(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String base64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record TestVerifierContext(
            MockRestServiceServer server,
            IdentityJwtPreflightVerifier verifier
    ) {
    }
}
