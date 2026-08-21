package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.IdentitySessionResponse;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentitySessionServiceTest {

    @Mock
    private IdentityPrincipalService identityPrincipalService;

    @Mock
    private IdentityRefreshTokenService identityRefreshTokenService;

    @Mock
    private IdentityAuditLogService identityAuditLogService;

    private IdentitySessionService identitySessionService;

    @BeforeEach
    void setUp() {
        identitySessionService = new IdentitySessionService(
                identityPrincipalService,
                identityRefreshTokenService,
                identityAuditLogService
        );
    }

    @Test
    void listCurrentUserSessionsDelegatesWithCurrentSessionId() {
        IdentityUser user = identityUser(18L);
        IdentityTokenService.TokenClaims claims = new IdentityTokenService.TokenClaims(18L, 2L, 51L, 4_200_000_000L);
        IdentitySessionResponse session = session(51L, true);

        when(identityPrincipalService.requireActivePrincipal("Bearer token"))
                .thenReturn(new IdentityPrincipalService.IdentityPrincipal(user, claims));
        when(identityRefreshTokenService.listUserSessions(18L, 51L, true))
                .thenReturn(List.of(session));

        List<IdentitySessionResponse> response =
                identitySessionService.listCurrentUserSessions("Bearer token", true);

        assertThat(response).containsExactly(session);
    }

    @Test
    void revokeCurrentUserSessionDelegatesAndAuditsSuccess() {
        IdentityUser user = identityUser(18L);
        IdentityTokenService.TokenClaims claims = new IdentityTokenService.TokenClaims(18L, 2L, 51L, 4_200_000_000L);

        when(identityPrincipalService.requireActivePrincipal("Bearer token"))
                .thenReturn(new IdentityPrincipalService.IdentityPrincipal(user, claims));

        identitySessionService.revokeCurrentUserSession("Bearer token", 52L);

        verify(identityRefreshTokenService).revokeUserSession(18L, 52L, "user_revoke_session");
        verify(identityAuditLogService).record(
                IdentityAuditEventType.LOGOUT,
                IdentityAuditOutcome.SUCCESS,
                18L,
                18L,
                "13800000000",
                "session_revoke:52"
        );
    }

    @Test
    void revokeCurrentUserSessionRejectsInvalidSessionIdAfterAuthLookup() {
        IdentityUser user = identityUser(18L);
        IdentityTokenService.TokenClaims claims = new IdentityTokenService.TokenClaims(18L, 2L, 51L, 4_200_000_000L);

        when(identityPrincipalService.requireActivePrincipal("Bearer token"))
                .thenReturn(new IdentityPrincipalService.IdentityPrincipal(user, claims));

        assertThatThrownBy(() -> identitySessionService.revokeCurrentUserSession("Bearer token", 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("登录会话编号不合法。");

        verifyNoInteractions(identityRefreshTokenService);
        verify(identityAuditLogService).record(
                IdentityAuditEventType.LOGOUT,
                IdentityAuditOutcome.FAILURE,
                18L,
                18L,
                "13800000000",
                "session_revoke:0"
        );
    }

    @Test
    void revokeCurrentUserSessionAuditsFailureAfterAuthLookup() {
        IdentityUser user = identityUser(18L);
        IdentityTokenService.TokenClaims claims = new IdentityTokenService.TokenClaims(18L, 2L, 51L, 4_200_000_000L);

        when(identityPrincipalService.requireActivePrincipal("Bearer token"))
                .thenReturn(new IdentityPrincipalService.IdentityPrincipal(user, claims));
        doThrow(new IllegalArgumentException("登录会话不存在。"))
                .when(identityRefreshTokenService).revokeUserSession(18L, 99L, "user_revoke_session");

        assertThatThrownBy(() -> identitySessionService.revokeCurrentUserSession("Bearer token", 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("登录会话不存在。");

        verify(identityAuditLogService).record(
                IdentityAuditEventType.LOGOUT,
                IdentityAuditOutcome.FAILURE,
                18L,
                18L,
                "13800000000",
                "session_revoke:99"
        );
    }

    private IdentitySessionResponse session(Long sessionId, boolean current) {
        return new IdentitySessionResponse(
                sessionId,
                LocalDateTime.of(2026, 8, 21, 16, 0),
                null,
                LocalDateTime.of(2026, 9, 20, 16, 0),
                null,
                null,
                "203.0.113.8",
                "JUnit",
                current
        );
    }

    private IdentityUser identityUser(Long id) {
        IdentityUser user = new IdentityUser();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "phoneNumber", "13800000000");
        ReflectionTestUtils.setField(user, "email", "email-user@example.com");
        ReflectionTestUtils.setField(user, "nickname", "Alicia");
        ReflectionTestUtils.setField(user, "passwordHash", "hash");
        ReflectionTestUtils.setField(user, "tokenVersion", 2L);
        ReflectionTestUtils.setField(user, "role", IdentityUserRole.USER);
        ReflectionTestUtils.setField(user, "status", IdentityUserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 4, 29, 15, 30));
        return user;
    }
}
