package com.alicia.cloudstorage.ragexecution.infrastructure.cloud;

import com.alicia.cloudstorage.ragexecution.config.RagExecutionCloudProperties;
import com.alicia.cloudstorage.ragexecution.port.CloudActionGateway;
import com.alicia.cloudstorage.ragexecution.port.ExecutionClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class HttpCloudActionGateway implements CloudActionGateway {

    static final String ACTION_PATH = "/internal/rag-execution/actions";
    static final String CALLER = "rag-execution";

    private final RagExecutionCloudProperties properties;
    private final CloudActionRequestHasher requestHasher;
    private final ObjectMapper objectMapper;
    private final ExecutionClock clock;
    private final RestClient restClient;

    public HttpCloudActionGateway(
            RagExecutionCloudProperties properties,
            CloudActionRequestHasher requestHasher,
            ObjectMapper objectMapper,
            ExecutionClock clock
    ) {
        this.properties = properties;
        this.requestHasher = requestHasher;
        this.objectMapper = objectMapper;
        this.clock = clock;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public CloudActionResult dispatch(CloudActionCommand command) {
        Instant issuedAt = clock.now().truncatedTo(ChronoUnit.MICROS);
        ObjectNode envelope = envelope(command, requestHasher.hash(command), issuedAt);
        byte[] body = serialize(envelope);
        String timestamp = Long.toString(issuedAt.getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String bodyHash = sha256(body);
        String signature = hmac(properties.serviceSecret(), String.join(
                "\n", "POST", ACTION_PATH, CALLER, timestamp, nonce, bodyHash
        ));
        try {
            byte[] response = restClient.post()
                    .uri(ACTION_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Alicia-Caller", CALLER)
                    .header("X-Alicia-Timestamp", timestamp)
                    .header("X-Alicia-Nonce", nonce)
                    .header("X-Alicia-Content-SHA256", bodyHash)
                    .header("X-Alicia-Signature", signature)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            return parseResponse(response, command);
        } catch (RestClientResponseException exception) {
            String errorCode = readErrorCode(exception.getResponseBodyAsByteArray());
            int status = exception.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500 || "step_execution_in_progress".equals(errorCode);
            throw new CloudActionDispatchException(errorCode, retryable, exception);
        } catch (RestClientException exception) {
            throw new CloudActionDispatchException("cloud_response_unknown", true, exception);
        }
    }

    private ObjectNode envelope(CloudActionCommand command, String requestHash, Instant issuedAt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("executionId", command.executionId().toString());
        body.put("stepId", command.stepId().toString());
        body.put("actorUserId", command.actorUserId());
        body.put("actionType", command.actionType().name());
        body.put("payloadSchemaVersion", command.payloadSchemaVersion());
        body.set("payload", command.payload());
        body.put("requestHash", requestHash);
        body.put("issuedAt", issuedAt.toString());
        return body;
    }

    private byte[] serialize(ObjectNode value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception exception) {
            throw new CloudActionDispatchException("cloud_request_serialization_failed", false, exception);
        }
    }

    private CloudActionResult parseResponse(byte[] response, CloudActionCommand command) {
        try {
            JsonNode root = objectMapper.readTree(response);
            if (!command.executionId().toString().equals(root.path("executionId").asText())
                    || !command.stepId().toString().equals(root.path("stepId").asText())
                    || !"SUCCEEDED".equals(root.path("status").asText())) {
                throw new CloudActionDispatchException("cloud_receipt_mismatch", false);
            }
            String resultCode = root.path("resultCode").asText("");
            JsonNode result = root.get("result");
            if (!resultCode.matches("[A-Z0-9_]{1,64}") || result == null || !result.isObject()) {
                throw new CloudActionDispatchException("cloud_receipt_invalid", false);
            }
            Instant completedAt = Instant.parse(root.path("completedAt").asText());
            return new CloudActionResult(resultCode, result, completedAt);
        } catch (CloudActionDispatchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CloudActionDispatchException("cloud_receipt_invalid", true, exception);
        }
    }

    private String readErrorCode(byte[] response) {
        try {
            String code = objectMapper.readTree(response).path("error").asText("");
            return code.matches("[a-z0-9_]{1,64}") ? code : "cloud_action_rejected";
        } catch (Exception exception) {
            return "cloud_action_rejected";
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static String hmac(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", exception);
        }
    }
}
