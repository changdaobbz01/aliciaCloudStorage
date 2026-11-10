package com.alicia.cloudstorage.rag.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpExecutionRegistrationClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void treatsAnUnreadableSuccessResponseAsAnUncertainRegistration() throws Exception {
        startServer(200, "not-json");

        assertThatThrownBy(() -> client().register(request(), "Bearer test"))
                .isInstanceOf(ExecutionRegistrationUncertainException.class);
    }

    @Test
    void keepsClientErrorsDeterministicInsteadOfPollingRecovery() throws Exception {
        startServer(400, "{\"error\":\"invalid_request\"}");

        assertThatThrownBy(() -> client().register(request(), "Bearer test"))
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .isNotInstanceOf(ExecutionRegistrationUncertainException.class);
    }

    private void startServer(int status, String responseBody) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/executions", exchange -> {
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
    }

    private HttpExecutionRegistrationClient client() {
        RagExecutionRegistrationProperties properties = new RagExecutionRegistrationProperties(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "phase-eight-j-registration-secret-123456",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Duration.ofMillis(100),
                Duration.ofMinutes(10)
        );
        return new HttpExecutionRegistrationClient(
                properties,
                new ObjectMapper().findAndRegisterModules(),
                Clock.systemUTC()
        );
    }

    private ExecutionRegistrationRequest request() {
        return new ExecutionRegistrationRequest(
                "action_plan_v2",
                "response-test",
                "conversation-test",
                "folder_create",
                "ap_response-test",
                "a".repeat(64),
                "LOW",
                "Create a test folder",
                List.of(),
                Instant.now().plusSeconds(600)
        );
    }
}
