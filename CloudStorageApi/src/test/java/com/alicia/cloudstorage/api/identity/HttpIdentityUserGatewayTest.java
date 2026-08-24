package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpIdentityUserGatewayTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void getUserDelegatesToIdentityApiInternalLookupAndMapsAccount() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/internal/users/6"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": 6,
                          "phoneNumber": null,
                          "email": "user@example.com",
                          "emailVerifiedAt": "2026-08-17T07:22:18",
                          "nickname": "Alicia",
                          "avatarUrl": "cos:user-avatars/6/avatar.png",
                          "tokenVersion": 1,
                          "role": "USER",
                          "status": "ACTIVE",
                          "createdAt": "2026-08-17T07:22:18"
                        }
                        """, MediaType.APPLICATION_JSON));

        var account = context.gateway().getUser(6L);

        assertThat(account.id()).isEqualTo(6L);
        assertThat(account.email()).isEqualTo("user@example.com");
        assertThat(account.avatarUrl()).isEqualTo("cos:user-avatars/6/avatar.png");
        assertThat(account.role()).isEqualTo(UserRole.USER);
        assertThat(account.status()).isEqualTo(UserStatus.ACTIVE);
        context.server().verify();
    }

    @Test
    void getUserNotFoundBecomesCloudBusinessError() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/internal/users/404"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> context.gateway().getUser(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("身份服务请求失败。");

        context.server().verify();
    }

    private TestGatewayContext newContext() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.baseUrl("http://identity.test").build();
        HttpIdentityUserGateway gateway = new HttpIdentityUserGateway(
                restClient,
                objectMapper,
                new IdentityGatewayTelemetry()
        );
        return new TestGatewayContext(server, gateway);
    }

    private record TestGatewayContext(
            MockRestServiceServer server,
            HttpIdentityUserGateway gateway
    ) {
    }
}
