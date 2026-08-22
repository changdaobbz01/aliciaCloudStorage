package com.alicia.cloudstorage.api.config;

import com.alicia.cloudstorage.api.identity.HttpIdentityAdminGateway;
import com.alicia.cloudstorage.api.identity.HttpIdentityAuthGateway;
import com.alicia.cloudstorage.api.identity.HttpIdentityUserGateway;
import com.alicia.cloudstorage.api.identity.IdentityApiClientProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpClientConfigTest {

    @Test
    void providesIdentityRestClientForIdentityGateways() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(
                    IdentityApiClientProperties.class,
                    () -> new IdentityApiClientProperties("http://identity.test", 2000L, 5000L)
            );
            context.register(HttpClientConfig.class);

            context.refresh();

            assertThat(context.getBean(RestClient.class)).isNotNull();
        }
    }

    @Test
    void identityRestClientAppliesIdentityReadTimeout() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1000L);
                byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            RestClient client = new HttpClientConfig()
                    .identityRestClient(new IdentityApiClientProperties(
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            1000L,
                            50L
                    ));

            assertThatThrownBy(() -> client.get().uri("/slow").retrieve().body(String.class))
                    .isInstanceOf(RestClientException.class);
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void identityRequestFactoryNormalizesInvalidTimeouts() throws Exception {
        var requestFactory = HttpClientConfig.identityRequestFactory(
                new IdentityApiClientProperties("http://identity.test", 0L, -5L)
        );

        assertThat(readIntField(requestFactory, "connectTimeout")).isEqualTo(1);
        assertThat(readIntField(requestFactory, "readTimeout")).isEqualTo(1);
    }

    @Test
    void identityGatewayBeansCanBeCreatedWithHttpAndJsonInfrastructure() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of(
                            "alicia.identity-api.base-url=http://identity.test",
                            "alicia.identity-api.connect-timeout-ms=1500",
                            "alicia.identity-api.read-timeout-ms=3500"
                    )
                    .applyTo(context);
            context.register(IdentityApiClientProperties.class);
            context.register(HttpClientConfig.class);
            context.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
            context.register(HttpIdentityAdminGateway.class, HttpIdentityAuthGateway.class, HttpIdentityUserGateway.class);

            context.refresh();

            assertThat(context.getBean(HttpIdentityAdminGateway.class)).isNotNull();
            assertThat(context.getBean(HttpIdentityAuthGateway.class)).isNotNull();
            assertThat(context.getBean(HttpIdentityUserGateway.class)).isNotNull();
        }
    }

    private static int readIntField(Object target, String fieldName) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
