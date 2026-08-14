package com.alicia.cloudstorage.api.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
public class StorageNodeChangeWebhookForwarder {

    private static final Logger log = LoggerFactory.getLogger(StorageNodeChangeWebhookForwarder.class);
    private static final String SIGNATURE_HEADER = "X-Alicia-Event-Signature";
    private static final String EVENT_ID_HEADER = "X-Alicia-Event-Id";

    private final StorageEventWebhookProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public StorageNodeChangeWebhookForwarder(
            StorageEventWebhookProperties properties,
            JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1L, properties.getConnectTimeoutMs())))
                .build();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void forward(StorageNodeChangeEvent event) {
        if (!properties.isEnabled() || event.isEmpty()) {
            return;
        }

        properties.validateEnabledConfiguration();
        StorageNodeChangeWebhookPayload payload = StorageNodeChangeWebhookPayload.from(event);
        String body = serialize(payload);
        String signature = sign(body);

        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getUrl()))
                .timeout(Duration.ofMillis(Math.max(1L, properties.getRequestTimeoutMs())))
                .header("Content-Type", "application/json")
                .header(EVENT_ID_HEADER, payload.eventId())
                .header(SIGNATURE_HEADER, "sha256=" + signature)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        log.warn("Failed to forward storage event id={}", payload.eventId(), error);
                        return;
                    }

                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        log.warn(
                                "Storage event webhook returned status={} id={}",
                                response.statusCode(),
                                payload.eventId()
                        );
                    }
                });
    }

    private String serialize(StorageNodeChangeWebhookPayload payload) {
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize storage event payload.", exception);
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign storage event payload.", exception);
        }
    }

    public record StorageNodeChangeWebhookPayload(
            String eventId,
            StorageNodeChangeType changeType,
            List<StorageNodeReference> nodeReferences,
            boolean includeDescendants,
            Instant occurredAt
    ) {

        public static StorageNodeChangeWebhookPayload from(StorageNodeChangeEvent event) {
            return new StorageNodeChangeWebhookPayload(
                    UUID.randomUUID().toString(),
                    event.changeType(),
                    event.nodeReferences(),
                    event.includeDescendants(),
                    Instant.now()
            );
        }
    }
}
