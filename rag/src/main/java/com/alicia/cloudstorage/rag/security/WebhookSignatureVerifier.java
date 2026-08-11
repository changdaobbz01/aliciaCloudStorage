package com.alicia.cloudstorage.rag.security;

import com.alicia.cloudstorage.rag.config.RagSecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class WebhookSignatureVerifier {

    private static final String SIGNATURE_PREFIX = "sha256=";

    private final RagSecurityProperties properties;

    public WebhookSignatureVerifier(RagSecurityProperties properties) {
        this.properties = properties;
    }

    public void verify(String body, String signatureHeader) {
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "RAG webhook secret is not configured."
            );
        }

        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Storage event signature is missing.");
        }

        String actualSignature = signatureHeader.substring(SIGNATURE_PREFIX.length()).trim();
        String expectedSignature = sign(body == null ? "" : body);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                actualSignature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Storage event signature is invalid.");
        }
    }

    public String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal((body == null ? "" : body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign webhook payload.", exception);
        }
    }
}
