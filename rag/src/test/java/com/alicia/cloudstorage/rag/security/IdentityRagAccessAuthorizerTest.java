package com.alicia.cloudstorage.rag.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IdentityRagAccessAuthorizerTest {

    @Test
    void allowsUserWithRagApplicationRole() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/me"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andRespond(withSuccess("""
                        {
                          "id": 7,
                          "status": "ACTIVE",
                          "role": "USER",
                          "appRoles": {
                            "rag": "RAG_USER"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        RagAccessPrincipal principal = context.authorizer().requireRagAccess("Bearer token");

        assertThat(principal.userId()).isEqualTo(7L);
        assertThat(principal.appCode()).isEqualTo("rag");
        assertThat(principal.role()).isEqualTo("RAG_USER");
        context.server().verify();
    }

    @Test
    void rejectsMissingAuthorizationBeforeCallingIdentity() {
        TestGatewayContext context = newContext();

        assertThatThrownBy(() -> context.authorizer().requireRagAccess(" "))
                .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
                );
        context.server().verify();
    }

    @Test
    void rejectsUserWithoutRagApplicationRole() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/me"))
                .andRespond(withSuccess("""
                        {
                          "id": 8,
                          "status": "ACTIVE",
                          "role": "USER",
                          "appRoles": {
                            "cloud": "CLOUD_USER"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> context.authorizer().requireRagAccess("Bearer token"))
                .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
                );
        context.server().verify();
    }

    @Test
    void mapsIdentityUnauthorizedToUnauthorized() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/me"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> context.authorizer().requireRagAccess("Bearer token"))
                .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
                );
        context.server().verify();
    }

    @Test
    void mapsIdentityOutageToServiceUnavailable() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/me"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> context.authorizer().requireRagAccess("Bearer token"))
                .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                );
        context.server().verify();
    }

    private TestGatewayContext newContext() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl("http://identity.test").build();
        return new TestGatewayContext(server, new IdentityRagAccessAuthorizer(restClient));
    }

    private record TestGatewayContext(
            MockRestServiceServer server,
            IdentityRagAccessAuthorizer authorizer
    ) {
    }
}
