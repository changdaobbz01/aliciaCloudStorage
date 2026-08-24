package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.IdentityLoginRequest;
import com.alicia.cloudstorage.identity.dto.IdentityLogoutRequest;
import com.alicia.cloudstorage.identity.dto.IdentityRefreshTokenRequest;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityAuthServiceTest {

    @Mock
    private IdentityUserRepository identityUserRepository;

    @Mock
    private IdentityCredentialService identityCredentialService;

    @Mock
    private IdentityTokenService identityTokenService;

    @Mock
    private IdentityPrincipalService identityPrincipalService;

    @Spy
    private IdentityUserInputNormalizer identityUserInputNormalizer = new IdentityUserInputNormalizer();

    @Mock
    private IdentityAuditLogService identityAuditLogService;

    @Mock
    private IdentityRefreshTokenService identityRefreshTokenService;

    @InjectMocks
    private IdentityAuthService identityAuthService;

    @Test
    void loginAcceptsEmailIdentifierAndReturnsToken() {
        IdentityUser user = identityUser(18L, IdentityUserStatus.ACTIVE);

        when(identityUserRepository.findByEmail("email-user@example.com")).thenReturn(Optional.of(user));
        when(identityCredentialService.matches("Passw0rd", "hash")).thenReturn(true);
        when(identityRefreshTokenService.issue(user, "203.0.113.8", "JUnit"))
                .thenReturn(new IdentityRefreshTokenService.IssuedRefreshToken(51L, "refresh-token", LocalDateTime.now()));
        when(identityTokenService.createToken(user, 51L)).thenReturn("token");

        var response = identityAuthService.login(
                new IdentityLoginRequest("Email-User@Example.COM", null, null, "Passw0rd"),
                "203.0.113.8",
                "JUnit"
        );

        assertThat(response.token()).isEqualTo("token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().id()).isEqualTo(18L);
        assertThat(response.user().email()).isEqualTo("email-user@example.com");
    }

    @Test
    void loginRejectsIncorrectPassword() {
        IdentityUser user = identityUser(18L, IdentityUserStatus.ACTIVE);

        when(identityUserRepository.findByEmail("email-user@example.com")).thenReturn(Optional.of(user));
        when(identityCredentialService.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> identityAuthService.login(
                new IdentityLoginRequest("email-user@example.com", null, null, "wrong"),
                "203.0.113.8",
                "JUnit"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("账号或密码不正确。");
    }

    @Test
    void loginRejectsDisabledUser() {
        IdentityUser user = identityUser(18L, IdentityUserStatus.DISABLED);

        when(identityUserRepository.findByPhoneNumber("13800000000")).thenReturn(Optional.of(user));
        when(identityCredentialService.matches("Passw0rd", "hash")).thenReturn(true);

        assertThatThrownBy(() -> identityAuthService.login(
                new IdentityLoginRequest("13800000000", null, null, "Passw0rd"),
                "203.0.113.8",
                "JUnit"
        )).isInstanceOf(IdentityAuthException.class)
                .hasMessage("当前账号已停用。");
    }

    @Test
    void meReturnsCurrentUserWhenTokenVersionMatches() {
        IdentityUser user = identityUser(18L, IdentityUserStatus.ACTIVE);

        when(identityPrincipalService.requireActiveUser("Bearer token")).thenReturn(user);

        var response = identityAuthService.me("Bearer token");

        assertThat(response.id()).isEqualTo(18L);
        assertThat(response.email()).isEqualTo("email-user@example.com");
    }

    @Test
    void meRejectsMissingBearerToken() {
        when(identityPrincipalService.requireActiveUser(null))
                .thenThrow(new IdentityAuthException("请先登录。"));

        assertThatThrownBy(() -> identityAuthService.me(null))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("请先登录。");
    }

    @Test
    void meRejectsStaleTokenVersion() {
        when(identityPrincipalService.requireActiveUser("Bearer token"))
                .thenThrow(new IdentityAuthException("登录状态已失效。"));

        assertThatThrownBy(() -> identityAuthService.me("Bearer token"))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("登录状态已失效。");
    }

    @Test
    void refreshTokenRotatesRefreshTokenAndReturnsNewAccessToken() {
        IdentityUser user = identityUser(18L, IdentityUserStatus.ACTIVE);

        when(identityRefreshTokenService.rotate("refresh-token", "203.0.113.8", "JUnit"))
                .thenReturn(new IdentityRefreshTokenService.RefreshedIdentitySession(
                        user,
                        51L,
                        "new-refresh-token",
                        LocalDateTime.now()
                ));
        when(identityTokenService.createToken(user, 51L)).thenReturn("new-token");

        var response = identityAuthService.refreshToken(
                new IdentityRefreshTokenRequest("refresh-token"),
                "203.0.113.8",
                "JUnit"
        );

        assertThat(response.token()).isEqualTo("new-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.user().id()).isEqualTo(18L);
        assertThat(user.getTokenVersion()).isEqualTo(2L);
    }

    @Test
    void refreshTokenRejectsMissingRefreshToken() {
        assertThatThrownBy(() -> identityAuthService.refreshToken(null, "203.0.113.8", "JUnit"))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("刷新令牌不能为空。");
    }

    @Test
    void logoutRevokesCurrentSessionWithoutChangingTokenVersion() {
        IdentityUser user = identityUser(18L, IdentityUserStatus.ACTIVE);
        IdentityTokenService.TokenClaims claims = new IdentityTokenService.TokenClaims(18L, 2L, 51L, 4_200_000_000L);

        when(identityPrincipalService.requireActivePrincipal("Bearer token"))
                .thenReturn(new IdentityPrincipalService.IdentityPrincipal(user, claims));

        identityAuthService.logout("Bearer token", null);

        assertThat(user.getTokenVersion()).isEqualTo(2L);
        verify(identityRefreshTokenService).revokeSession(51L, "logout");
        verify(identityUserRepository, never()).save(user);
    }

    @Test
    void logoutAllDevicesInvalidatesUserTokenVersionAndRefreshSessions() {
        IdentityUser user = identityUser(18L, IdentityUserStatus.ACTIVE);
        IdentityTokenService.TokenClaims claims = new IdentityTokenService.TokenClaims(18L, 2L, 51L, 4_200_000_000L);

        when(identityPrincipalService.requireActivePrincipal("Bearer token"))
                .thenReturn(new IdentityPrincipalService.IdentityPrincipal(user, claims));

        identityAuthService.logout("Bearer token", new IdentityLogoutRequest(null, true));

        assertThat(user.getTokenVersion()).isEqualTo(3L);
        verify(identityRefreshTokenService).revokeAllForUser(18L, "logout_all_devices");
        verify(identityUserRepository).save(user);
    }

    private IdentityUser identityUser(Long id, IdentityUserStatus status) {
        IdentityUser user = newIdentityUser();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "phoneNumber", "13800000000");
        ReflectionTestUtils.setField(user, "email", "email-user@example.com");
        ReflectionTestUtils.setField(user, "emailVerifiedAt", LocalDateTime.of(2026, 8, 17, 15, 30));
        ReflectionTestUtils.setField(user, "nickname", "Alicia");
        ReflectionTestUtils.setField(user, "avatarUrl", "cos:user-avatars/18/avatar.webp");
        ReflectionTestUtils.setField(user, "passwordHash", "hash");
        ReflectionTestUtils.setField(user, "tokenVersion", 2L);
        ReflectionTestUtils.setField(user, "role", IdentityUserRole.USER);
        ReflectionTestUtils.setField(user, "status", status);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 4, 29, 15, 30));
        return user;
    }

    private IdentityUser newIdentityUser() {
        try {
            var constructor = IdentityUser.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Failed to create IdentityUser test fixture.", ex);
        }
    }
}
