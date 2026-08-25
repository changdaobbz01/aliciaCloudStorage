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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
        assertThat(context.telemetry().snapshots())
                .singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.operation()).isEqualTo("auth.me");
                    assertThat(snapshot.successCount()).isEqualTo(1L);
                    assertThat(snapshot.failureCount()).isZero();
                });
        context.server().verify();
    }

    @Test
    void meUsesCachedSnapshotWithinShortTtlAndStillRunsPreflight() {
        AtomicInteger preflightCalls = new AtomicInteger();
        TestGatewayContext context = newContext(authorization -> preflightCalls.incrementAndGet());
        expectMeResponse(context, "Bearer user-token", "Alicia");

        var first = context.gateway().me("Bearer user-token");
        var second = context.gateway().me("Bearer user-token");

        assertThat(second).isSameAs(first);
        assertThat(preflightCalls).hasValue(2);
        assertThat(context.telemetry().snapshots())
                .extracting(IdentityGatewayOperationSnapshot::operation)
                .containsExactly("auth.me", "auth.me.cacheHit");
        context.server().verify();
    }

    @Test
    void meRefreshesSnapshotAfterCacheEntryExpires() {
        AtomicLong now = new AtomicLong(1000L);
        IdentityCurrentUserCache cache = new IdentityCurrentUserCache(
                true,
                Duration.ofMillis(50L),
                128,
                now::get
        );
        TestGatewayContext context = newContext(authorization -> {
        }, cache);
        expectMeResponse(context, "Bearer user-token", "Alicia");
        expectMeResponse(context, "Bearer user-token", "Updated Alicia");

        var first = context.gateway().me("Bearer user-token");
        now.set(1100L);
        var second = context.gateway().me("Bearer user-token");

        assertThat(first.nickname()).isEqualTo("Alicia");
        assertThat(second.nickname()).isEqualTo("Updated Alicia");
        assertThat(context.telemetry().snapshots())
                .singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.operation()).isEqualTo("auth.me");
                    assertThat(snapshot.successCount()).isEqualTo(2L);
                });
        context.server().verify();
    }

    @Test
    void meEvictsCachedSnapshotWhenCacheHitPreflightFails() {
        AtomicInteger preflightCalls = new AtomicInteger();
        TestGatewayContext context = newContext(authorization -> {
            if (preflightCalls.incrementAndGet() == 2) {
                throw new PrincipalAccessException("Token 签名校验失败。");
            }
        });
        expectMeResponse(context, "Bearer user-token", "Alicia");

        context.gateway().me("Bearer user-token");

        assertThatThrownBy(() -> context.gateway().me("Bearer user-token"))
                .isInstanceOf(PrincipalAccessException.class)
                .hasMessage("Token 签名校验失败。");
        assertThat(context.cache().size()).isZero();
        context.server().verify();
    }

    @Test
    void meStopsBeforeIdentityWhenTokenPreflightFails() {
        TestGatewayContext context = newContext(authorization -> {
            throw new PrincipalAccessException("Token 签名校验失败。");
        });

        assertThatThrownBy(() -> context.gateway().me("Bearer bad-token"))
                .isInstanceOf(PrincipalAccessException.class)
                .hasMessage("Token 签名校验失败。");

        assertThat(context.telemetry().snapshots())
                .singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.operation()).isEqualTo("auth.me");
                    assertThat(snapshot.successCount()).isZero();
                    assertThat(snapshot.failureCount()).isEqualTo(1L);
                    assertThat(snapshot.lastError()).isEqualTo("PrincipalAccessException");
                });
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
    void updateProfileRefreshesCachedCurrentUserSnapshot() {
        TestGatewayContext context = newContext();
        expectMeResponse(context, "Bearer user-token", "Alicia");
        context.server().expect(requestTo("http://identity.test/api/identity/auth/profile"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
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

        var before = context.gateway().me("Bearer user-token");
        context.gateway().updateProfile(
                "Bearer user-token",
                new UpdateProfileRequest("13900000000", "Updated Alicia", "cos:user-avatars/6/new.png")
        );
        var after = context.gateway().me("Bearer user-token");

        assertThat(before.nickname()).isEqualTo("Alicia");
        assertThat(after.nickname()).isEqualTo("Updated Alicia");
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
        return newContext(authorization -> {
        });
    }

    private TestGatewayContext newContext(IdentityAccessTokenPreflightVerifier preflightVerifier) {
        return newContext(
                preflightVerifier,
                new IdentityCurrentUserCache(true, Duration.ofSeconds(5L), 128, System::currentTimeMillis)
        );
    }

    private TestGatewayContext newContext(
            IdentityAccessTokenPreflightVerifier preflightVerifier,
            IdentityCurrentUserCache cache
    ) {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.baseUrl("http://identity.test").build();
        IdentityGatewayTelemetry telemetry = new IdentityGatewayTelemetry();
        HttpIdentityAuthGateway gateway = new HttpIdentityAuthGateway(
                restClient,
                objectMapper,
                preflightVerifier,
                telemetry,
                cache
        );
        return new TestGatewayContext(server, gateway, telemetry, cache);
    }

    private void expectMeResponse(TestGatewayContext context, String authorization, String nickname) {
        context.server().expect(requestTo("http://identity.test/api/identity/auth/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authorization))
                .andRespond(withSuccess("""
                        {
                          "id": 6,
                          "phoneNumber": null,
                          "email": "user@example.com",
                          "emailVerifiedAt": "2026-08-17T07:22:18",
                          "nickname": "%s",
                          "avatarUrl": "cos:user-avatars/6/avatar.png",
                          "tokenVersion": 1,
                          "role": "USER",
                          "status": "ACTIVE",
                          "createdAt": "2026-08-17T07:22:18"
                        }
                        """.formatted(nickname), MediaType.APPLICATION_JSON));
    }

    private record TestGatewayContext(
            MockRestServiceServer server,
            HttpIdentityAuthGateway gateway,
            IdentityGatewayTelemetry telemetry,
            IdentityCurrentUserCache cache
    ) {
    }
}
