package com.alicia.cloudstorage.ragexecution.infrastructure.cloud;

import com.alicia.cloudstorage.ragexecution.application.CanonicalJsonHasher;
import com.alicia.cloudstorage.ragexecution.config.RagExecutionCloudProperties;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;
import com.alicia.cloudstorage.ragexecution.port.CloudActionGateway.CloudActionCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpCloudActionGatewayTest {

    private static final String SECRET = "phase-four-cloud-action-secret-123456789";
    private static final Instant NOW = Instant.parse("2026-09-18T08:00:00Z");

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsExactSignedBodyAndCloudCompatibleRequestHash() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AtomicReference<JsonNode> captured = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(HttpCloudActionGateway.ACTION_PATH, exchange -> handle(exchange, objectMapper, captured));
        server.start();

        RagExecutionCloudProperties properties = new RagExecutionCloudProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                SECRET,
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)
        );
        CanonicalJsonHasher canonicalJsonHasher = new CanonicalJsonHasher(objectMapper);
        HttpCloudActionGateway gateway = new HttpCloudActionGateway(
                properties,
                new CloudActionRequestHasher(objectMapper, canonicalJsonHasher),
                objectMapper,
                () -> NOW
        );
        UUID executionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID stepId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ObjectNode payload = objectMapper.createObjectNode()
                .put("nodeId", 7)
                .put("expectedNodeVersion", 0)
                .put("newName", "renamed.txt");

        var result = gateway.dispatch(new CloudActionCommand(
                executionId,
                stepId,
                42L,
                ExecutionActionType.NODE_RENAME,
                "rag_execution_action_v1",
                payload
        ));

        assertThat(result.resultCode()).isEqualTo("NODE_RENAMED");
        String canonical = "{\"actionType\":\"NODE_RENAME\",\"actorUserId\":42,"
                + "\"executionId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"payload\":{\"expectedNodeVersion\":0,\"newName\":\"renamed.txt\",\"nodeId\":7},"
                + "\"payloadSchemaVersion\":\"rag_execution_action_v1\","
                + "\"stepId\":\"22222222-2222-2222-2222-222222222222\"}";
        assertThat(captured.get().path("requestHash").asText())
                .isEqualTo(sha256(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsSuccessfulReceiptWithoutAResultContractAndDoesNotRetryIt() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(HttpCloudActionGateway.ACTION_PATH, exchange -> {
            JsonNode request = objectMapper.readTree(exchange.getRequestBody().readAllBytes());
            ObjectNode response = objectMapper.createObjectNode();
            response.put("executionId", request.path("executionId").asText());
            response.put("stepId", request.path("stepId").asText());
            response.put("status", "SUCCEEDED");
            response.put("resultCode", "");
            response.put("completedAt", NOW.plusSeconds(1).toString());
            byte[] responseBody = objectMapper.writeValueAsBytes(response);
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
        HttpCloudActionGateway gateway = new HttpCloudActionGateway(
                new RagExecutionCloudProperties(
                        "http://127.0.0.1:" + server.getAddress().getPort(),
                        SECRET,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(2)
                ),
                new CloudActionRequestHasher(objectMapper, new CanonicalJsonHasher(objectMapper)),
                objectMapper,
                () -> NOW
        );
        CloudActionCommand command = new CloudActionCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                42L,
                ExecutionActionType.FOLDER_CREATE,
                "rag_execution_action_v1",
                objectMapper.createObjectNode().put("folderName", "safe-folder")
        );

        assertThatThrownBy(() -> gateway.dispatch(command))
                .isInstanceOfSatisfying(CloudActionDispatchException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo("cloud_receipt_invalid");
                    assertThat(exception.retryable()).isFalse();
                });
    }

    private void handle(
            HttpExchange exchange,
            ObjectMapper objectMapper,
            AtomicReference<JsonNode> captured
    ) throws java.io.IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        String bodyHash = sha256(body);
        assertThat(exchange.getRequestHeaders().getFirst("X-Alicia-Content-SHA256")).isEqualTo(bodyHash);
        String canonicalSignature = String.join(
                "\n",
                "POST",
                HttpCloudActionGateway.ACTION_PATH,
                HttpCloudActionGateway.CALLER,
                exchange.getRequestHeaders().getFirst("X-Alicia-Timestamp"),
                exchange.getRequestHeaders().getFirst("X-Alicia-Nonce"),
                bodyHash
        );
        assertThat(exchange.getRequestHeaders().getFirst("X-Alicia-Signature"))
                .isEqualTo(hmac(canonicalSignature));
        JsonNode request = objectMapper.readTree(body);
        captured.set(request);
        ObjectNode response = objectMapper.createObjectNode();
        response.put("executionId", request.path("executionId").asText());
        response.put("stepId", request.path("stepId").asText());
        response.put("status", "SUCCEEDED");
        response.put("resultCode", "NODE_RENAMED");
        response.set("result", objectMapper.createObjectNode().put("nodeId", 7).put("entityVersion", 1));
        response.put("completedAt", NOW.plusSeconds(1).toString());
        byte[] responseBody = objectMapper.writeValueAsBytes(response);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String hmac(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
