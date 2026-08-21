package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.IdentityLoginRequest;
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

    @InjectMocks
    private IdentityAuthService identityAuthService;

    @Test
    void loginAcceptsEmailIdentifierAndReturnsToken() {
        IdentityUser user = identityUser(18L, IdentityUserStatus.ACTIVE);

        when(identityUserRepository.findByEmail("email-user@example.com")).thenReturn(Optional.of(user));
        when(identityCredentialService.matches("Passw0rd", "hash")).thenReturn(true);
        when(identityTokenService.createToken(user)).thenReturn("token");

        var response = identityAuthService.login(
                new IdentityLoginRequest("Email-User@Example.COM", null, null, "Passw0rd")
        );

        assertThat(response.token()).isEqualTo("token");
        assertThat(response.user().id()).isEqualTo(18L);
        assertThat(response.user().email()).isEqualTo("email-user@example.com");
    }

    @Test
    void loginRejectsIncorrectPassword() {
        IdentityUser user = identityUser(18L, IdentityUserStatus.ACTIVE);

        when(identityUserRepository.findByEmail("email-user@example.com")).thenReturn(Optional.of(user));
        when(identityCredentialService.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> identityAuthService.login(
                new IdentityLoginRequest("email-user@example.com", null, null, "wrong")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("账号或密码不正确。");
    }

    @Test
    void loginRejectsDisabledUser() {
        IdentityUser user = identityUser(18L, IdentityUserStatus.DISABLED);

        when(identityUserRepository.findByPhoneNumber("13800000000")).thenReturn(Optional.of(user));
        when(identityCredentialService.matches("Passw0rd", "hash")).thenReturn(true);

        assertThatThrownBy(() -> identityAuthService.login(
                new IdentityLoginRequest("13800000000", null, null, "Passw0rd")
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
    void refreshTokenReturnsNewTokenWithoutChangingTokenVersion() {
        IdentityUser user = identityUser(18L, IdentityUserStatus.ACTIVE);

        when(identityPrincipalService.requireActiveUser("Bearer token")).thenReturn(user);
        when(identityTokenService.createToken(user)).thenReturn("new-token");

        var response = identityAuthService.refreshToken("Bearer token");

        assertThat(response.token()).isEqualTo("new-token");
        assertThat(response.user().id()).isEqualTo(18L);
        assertThat(user.getTokenVersion()).isEqualTo(2L);
    }

    @Test
    void logoutInvalidatesCurrentUserTokens() {
        IdentityUser user = identityUser(18L, IdentityUserStatus.ACTIVE);

        when(identityPrincipalService.requireActiveUser("Bearer token")).thenReturn(user);

        identityAuthService.logout("Bearer token");

        assertThat(user.getTokenVersion()).isEqualTo(3L);
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
