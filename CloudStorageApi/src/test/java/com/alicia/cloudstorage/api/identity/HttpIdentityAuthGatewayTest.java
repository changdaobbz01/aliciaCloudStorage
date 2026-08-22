package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.principal.PrincipalAccessException;
import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpIdentityAuthGatewayTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void meDelegatesToIdentityApiAndMapsAccount() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
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

        var account = context.gateway().me("Bearer user-token");

        assertThat(account.id()).isEqualTo(6L);
        assertThat(account.email()).isEqualTo("user@example.com");
        assertThat(account.role()).isEqualTo(UserRole.USER);
        assertThat(account.status()).isEqualTo(UserStatus.ACTIVE);
        context.server().verify();
    }

    @Test
    void meAuthFailureBecomesCloudPrincipalAccessException() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/me"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":401,"error":"请先登录。","timestamp":"2026-08-19T09:30:00Z"}
                                """));

        assertThatThrownBy(() -> context.gateway().me("Bearer stale-token"))
                .isInstanceOf(PrincipalAccessException.class)
                .hasMessage("请先登录。");

        context.server().verify();
    }

    @Test
    void meEmptyBodyBecomesIdentityUnavailable() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/me"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> context.gateway().me("Bearer user-token"))
                .isInstanceOf(IdentityServiceUnavailableException.class)
                .hasMessage("身份服务当前用户响应为空。");

        context.server().verify();
    }

    @Test
    void meMalformedBodyBecomesIdentityUnavailable() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/me"))
                .andRespond(withSuccess("""
                        {
                          "id": 6,
                          "email": "user@example.com",
                          "createdAt": "2026-08-17T07:22:18"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> context.gateway().me("Bearer user-token"))
                .isInstanceOf(IdentityServiceUnavailableException.class)
                .hasMessage("身份服务当前用户响应格式异常。");

        context.server().verify();
    }

    @Test
    void updateProfileDelegatesToIdentityApiAndMapsAccount() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/profile"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andExpect(content().string(containsString("\"phoneNumber\":\"13900000000\"")))
                .andExpect(content().string(containsString("\"nickname\":\"Updated Alicia\"")))
                .andExpect(content().string(containsString("\"avatarUrl\":\"cos:user-avatars/6/new.png\"")))
                .andRespond(withSuccess("""
                        {
                          "id": 6,
                          "phoneNumber": "13900000000",
                          "email": "user@example.com",
                          "emailVerifiedAt": "2026-08-17T07:22:18",
                          "nickname": "Updated Alicia",
                          "avatarUrl": "cos:user-avatars/6/new.png",
                          "tokenVersion": 1,
                          "role": "USER",
                          "status": "ACTIVE",
                          "createdAt": "2026-08-17T07:22:18"
                        }
                        """, MediaType.APPLICATION_JSON));

        var account = context.gateway().updateProfile(
                "Bearer user-token",
                new UpdateProfileRequest("13900000000", "Updated Alicia", "cos:user-avatars/6/new.png")
        );

        assertThat(account.phoneNumber()).isEqualTo("13900000000");
        assertThat(account.nickname()).isEqualTo("Updated Alicia");
        assertThat(account.avatarUrl()).isEqualTo("cos:user-avatars/6/new.png");
        context.server().verify();
    }

    @Test
    void updateProfileAuthFailureBecomesCloudPrincipalAccessException() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/profile"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":401,"error":"请先登录。","timestamp":"2026-08-19T09:30:00Z"}
                                """));

        assertThatThrownBy(() -> context.gateway().updateProfile(
                "Bearer stale-token",
                new UpdateProfileRequest("13900000000", "Updated Alicia", null)
        )).isInstanceOf(PrincipalAccessException.class)
                .hasMessage("请先登录。");

        context.server().verify();
    }

    private TestGatewayContext newContext() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        HttpIdentityAuthGateway gateway = new HttpIdentityAuthGateway(
                restClientBuilder,
                objectMapper,
                "http://identity.test"
        );
        return new TestGatewayContext(server, gateway);
    }

    private record TestGatewayContext(
            MockRestServiceServer server,
            HttpIdentityAuthGateway gateway
    ) {
    }
}
