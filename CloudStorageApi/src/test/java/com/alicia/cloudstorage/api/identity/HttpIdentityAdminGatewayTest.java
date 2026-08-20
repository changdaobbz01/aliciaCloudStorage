package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.auth.AuthException;
import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpIdentityAdminGatewayTest {

    private final JsonMapper objectMapper = JsonMapper.builder()
            .build();

    @Test
    void listUsersCallsIdentityApiAndMapsAccounts() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/admin/users"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andRespond(withSuccess("""
                        [
                          {
                            "id": 7,
                            "phoneNumber": null,
                            "email": "user@example.com",
                            "emailVerifiedAt": null,
                            "nickname": "Alicia",
                            "avatarUrl": "cos:user-avatars/7/avatar.webp",
                            "tokenVersion": 0,
                            "role": "USER",
                            "status": "ACTIVE",
                            "createdAt": "2026-08-19T09:30:00"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<IdentityUserSnapshot> accounts = context.gateway().listUsers("Bearer admin-token");

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).id()).isEqualTo(7L);
        assertThat(accounts.get(0).email()).isEqualTo("user@example.com");
        assertThat(accounts.get(0).role()).isEqualTo(UserRole.USER);
        assertThat(accounts.get(0).status()).isEqualTo(UserStatus.ACTIVE);
        context.server().verify();
    }

    @Test
    void createUserPostsIdentityOnlyPayload() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/admin/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(content().string(containsString("\"phoneNumber\":\"13800000001\"")))
                .andExpect(content().string(not(containsString("storageQuotaBytes"))))
                .andExpect(content().string(not(containsString("inheritAdminBackground"))))
                .andRespond(withSuccess("""
                        {
                          "id": 8,
                          "phoneNumber": "13800000001",
                          "email": null,
                          "emailVerifiedAt": null,
                          "nickname": "New User",
                          "avatarUrl": null,
                          "tokenVersion": 0,
                          "role": "USER",
                          "status": "ACTIVE",
                          "createdAt": "2026-08-19T09:30:00"
                        }
                        """, MediaType.APPLICATION_JSON));
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "13800000001",
                "New User",
                null,
                true,
                "Passw0rd",
                "USER",
                4096L
        );

        IdentityUserSnapshot account = context.gateway().createUser("Bearer admin-token", request);

        assertThat(account.id()).isEqualTo(8L);
        assertThat(account.phoneNumber()).isEqualTo("13800000001");
        context.server().verify();
    }

    @Test
    void resetUserPasswordDelegatesToIdentityApi() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/admin/users/8/password"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(content().string(containsString("\"newPassword\":\"ResetPass1\"")))
                .andRespond(withSuccess("""
                        {"message":"用户密码已重置，旧登录状态已失效。"}
                        """, MediaType.APPLICATION_JSON));

        context.gateway().resetUserPassword(
                "Bearer admin-token",
                8L,
                new AdminResetUserPasswordRequest("ResetPass1")
        );

        context.server().verify();
    }

    @Test
    void identityAuthFailureBecomesCloudAuthException() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/admin/users"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":401,"error":"当前接口仅允许管理员访问。","timestamp":"2026-08-19T09:30:00Z"}
                                """));

        assertThatThrownBy(() -> context.gateway().listUsers("Bearer user-token"))
                .isInstanceOf(AuthException.class)
                .hasMessage("当前接口仅允许管理员访问。");

        context.server().verify();
    }

    private TestGatewayContext newContext() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        HttpIdentityAdminGateway gateway = new HttpIdentityAdminGateway(
                restClientBuilder,
                objectMapper,
                "http://identity.test"
        );
        return new TestGatewayContext(server, gateway);
    }

    private record TestGatewayContext(
            MockRestServiceServer server,
            HttpIdentityAdminGateway gateway
    ) {
    }
}
