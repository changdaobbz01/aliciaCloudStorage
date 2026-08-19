package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.auth.AuthException;
import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpIdentityAuthGatewayTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void changePasswordDelegatesToIdentityApi() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/password"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andExpect(content().string(containsString("\"oldPassword\":\"OldPass1\"")))
                .andExpect(content().string(containsString("\"newPassword\":\"NewPass1\"")))
                .andRespond(withSuccess("""
                        {"message":"密码修改成功。"}
                        """, MediaType.APPLICATION_JSON));

        context.gateway().changePassword(
                "Bearer user-token",
                new ChangePasswordRequest("OldPass1", "NewPass1")
        );

        context.server().verify();
    }

    @Test
    void staleTokenBecomesCloudAuthException() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/password"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":401,"error":"登录状态已失效。","timestamp":"2026-08-19T09:30:00Z"}
                                """));

        assertThatThrownBy(() -> context.gateway().changePassword(
                "Bearer stale-token",
                new ChangePasswordRequest("OldPass1", "NewPass1")
        )).isInstanceOf(AuthException.class)
                .hasMessage("登录状态已失效。");

        context.server().verify();
    }

    @Test
    void invalidOldPasswordBecomesCloudBusinessError() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/password"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":400,"error":"旧密码不正确。","timestamp":"2026-08-19T09:30:00Z"}
                                """));

        assertThatThrownBy(() -> context.gateway().changePassword(
                "Bearer user-token",
                new ChangePasswordRequest("wrong", "NewPass1")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("旧密码不正确。");

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
