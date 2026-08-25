package com.alicia.cloudstorage.api.identity;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IdentityDependencyHealthServiceTest {

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

        IdentityDependencyHealth health = context.service().check();

        assertThat(health.available()).isTrue();
        assertThat(health.status()).isEqualTo("ok");
        assertThat(health.service()).isEqualTo("alicia-identity-api");
        assertThat(health.currentUserCache())
                .satisfies(cache -> {
                    assertThat(cache.enabled()).isTrue();
                    assertThat(cache.ttlMillis()).isEqualTo(3000L);
                    assertThat(cache.maxEntries()).isEqualTo(128);
                });
        assertThat(health.operations())
                .singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.operation()).isEqualTo("auth.me");
                    assertThat(snapshot.successCount()).isEqualTo(1L);
                });
        context.server().verify();
    }

    @Test
    void reportsIdentityUnavailableWhenHealthEndpointReturnsUnexpectedStatus() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/health"))
                .andRespond(withSuccess("""
                        {
                          "status": "starting",
                          "service": "alicia-identity-api"
                        }
                        """, MediaType.APPLICATION_JSON));

        IdentityDependencyHealth health = context.service().check();

        assertThat(health.available()).isFalse();
        assertThat(health.status()).isEqualTo("unavailable");
        assertThat(health.operations()).isNotEmpty();
        context.server().verify();
    }

    @Test
    void reportsIdentityUnavailableWhenHealthRequestFails() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/health"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        IdentityDependencyHealth health = context.service().check();

        assertThat(health.available()).isFalse();
        assertThat(health.status()).isEqualTo("unavailable");
        assertThat(health.operations()).isNotEmpty();
        context.server().verify();
    }

    private TestGatewayContext newContext() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.baseUrl("http://identity.test").build();
        IdentityGatewayTelemetry telemetry = new IdentityGatewayTelemetry();
        telemetry.observe("auth.me", () -> "ok");
        IdentityCurrentUserCache cache = new IdentityCurrentUserCache(
                true,
                java.time.Duration.ofSeconds(3L),
                128,
                System::currentTimeMillis
        );
        return new TestGatewayContext(server, new IdentityDependencyHealthService(restClient, telemetry, cache));
    }

    private record TestGatewayContext(
            MockRestServiceServer server,
            IdentityDependencyHealthService service
    ) {
    }
}
