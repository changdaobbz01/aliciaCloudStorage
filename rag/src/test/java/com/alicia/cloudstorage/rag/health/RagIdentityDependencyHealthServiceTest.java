package com.alicia.cloudstorage.rag.health;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RagIdentityDependencyHealthServiceTest {

    @Test
    void reportsIdentityAvailableWhenHealthEndpointReturnsOk() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/health"))
                .andRespond(withSuccess("""
                        {
                          "status": "ok",
                          "service": "alicia-identity-api"
                        }
                        """, MediaType.APPLICATION_JSON));

        RagDependencyHealth health = context.service().check();

        assertThat(health.available()).isTrue();
        assertThat(health.status()).isEqualTo("ok");
        assertThat(health.service()).isEqualTo("alicia-identity-api");
        assertThat(health.operations())
                .singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.operation()).isEqualTo("identity.health");
                    assertThat(snapshot.successCount()).isEqualTo(1L);
                });
        context.server().verify();
    }

    @Test
    void reportsIdentityUnavailableWhenHealthRequestFails() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/health"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        RagDependencyHealth health = context.service().check();

        assertThat(health.available()).isFalse();
        assertThat(health.status()).isEqualTo("unavailable");
        assertThat(health.operations())
                .singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.operation()).isEqualTo("identity.health");
                    assertThat(snapshot.failureCount()).isEqualTo(1L);
                });
        context.server().verify();
    }

    private TestGatewayContext newContext() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.baseUrl("http://identity.test").build();
        return new TestGatewayContext(
                server,
                new RagIdentityDependencyHealthService(restClient, new RagDependencyTelemetry())
        );
    }

    private record TestGatewayContext(
            MockRestServiceServer server,
            RagIdentityDependencyHealthService service
    ) {
    }
}
