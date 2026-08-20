package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.auth.AuthException;
import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.RequestEmailRegistrationCodeRequest;
import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import com.alicia.cloudstorage.api.dto.VerifyEmailRegistrationRequest;
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
    void loginDelegatesToIdentityApiAndMapsSession() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"identifier\":\"user@example.com\"")))
                .andExpect(content().string(containsString("\"password\":\"Passw0rd\"")))
                .andRespond(withSuccess("""
                        {
                          "token": "identity-token",
                          "user": {
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
                        }
                        """, MediaType.APPLICATION_JSON));

        IdentityLoginSession session = context.gateway().login(
                new LoginRequest("user@example.com", null, null, "Passw0rd")
        );

        assertThat(session.token()).isEqualTo("identity-token");
        assertThat(session.account().id()).isEqualTo(6L);
        assertThat(session.account().email()).isEqualTo("user@example.com");
        assertThat(session.account().role()).isEqualTo(UserRole.USER);
        assertThat(session.account().status()).isEqualTo(UserStatus.ACTIVE);
        context.server().verify();
    }

    @Test
    void loginFailureBecomesCloudBusinessError() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/login"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":400,"error":"账号或密码不正确。","timestamp":"2026-08-19T09:30:00Z"}
                                """));

        assertThatThrownBy(() -> context.gateway().login(
                new LoginRequest("user@example.com", null, null, "wrong")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("账号或密码不正确。");

        context.server().verify();
    }

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
    void meAuthFailureBecomesCloudAuthException() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/me"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":401,"error":"请先登录。","timestamp":"2026-08-19T09:30:00Z"}
                                """));

        assertThatThrownBy(() -> context.gateway().me("Bearer stale-token"))
                .isInstanceOf(AuthException.class)
                .hasMessage("请先登录。");

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
    void updateProfileAuthFailureBecomesCloudAuthException() {
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
        )).isInstanceOf(AuthException.class)
                .hasMessage("请先登录。");

        context.server().verify();
    }

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

    @Test
    void requestEmailRegistrationCodeDelegatesToIdentityApiWithClientMetadata() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/register/email-code"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Forwarded-For", "203.0.113.8"))
                .andExpect(header(HttpHeaders.USER_AGENT, "JUnit"))
                .andExpect(content().string(containsString("\"email\":\"NewUser@Example.COM\"")))
                .andRespond(withSuccess("""
                        {"message":"如果邮箱可用，验证码会发送到该邮箱。"}
                        """, MediaType.APPLICATION_JSON));

        context.gateway().requestEmailRegistrationCode(
                new RequestEmailRegistrationCodeRequest("NewUser@Example.COM"),
                "203.0.113.8",
                "JUnit"
        );

        context.server().verify();
    }

    @Test
    void upstreamServiceFailureBecomesIdentityUnavailableError() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/register/email-code"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":503,"error":"验证码邮件发送失败，请稍后再试。","timestamp":"2026-08-19T09:30:00Z"}
                                """));

        assertThatThrownBy(() -> context.gateway().requestEmailRegistrationCode(
                new RequestEmailRegistrationCodeRequest("NewUser@Example.COM"),
                "203.0.113.8",
                "JUnit"
        )).isInstanceOf(IdentityServiceUnavailableException.class)
                .hasMessage("验证码邮件发送失败，请稍后再试。");

        context.server().verify();
    }

    @Test
    void verifyEmailRegistrationDelegatesToIdentityApiAndMapsSession() {
        TestGatewayContext context = newContext();
        context.server().expect(requestTo("http://identity.test/api/identity/auth/register/verify"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"email\":\"NewUser@Example.COM\"")))
                .andExpect(content().string(containsString("\"code\":\"123456\"")))
                .andExpect(content().string(containsString("\"nickname\":\"New User\"")))
                .andRespond(withSuccess("""
                        {
                          "token": "new-token",
                          "user": {
                            "id": 8,
                            "phoneNumber": null,
                            "email": "newuser@example.com",
                            "emailVerifiedAt": "2026-08-17T07:22:18",
                            "nickname": "New User",
                            "avatarUrl": null,
                            "tokenVersion": 0,
                            "role": "USER",
                            "status": "ACTIVE",
                            "createdAt": "2026-08-17T07:22:18"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        IdentityLoginSession session = context.gateway().verifyEmailRegistration(
                new VerifyEmailRegistrationRequest("NewUser@Example.COM", "123456", "New User", "Passw0rd")
        );

        assertThat(session.token()).isEqualTo("new-token");
        assertThat(session.account().id()).isEqualTo(8L);
        assertThat(session.account().email()).isEqualTo("newuser@example.com");
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
