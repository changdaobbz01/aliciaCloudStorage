package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdentityPrincipalServiceTest {

    @Mock
    private IdentityUserRepository identityUserRepository;

    @Mock
    private IdentityTokenService identityTokenService;

    @Mock
    private IdentityRefreshTokenService identityRefreshTokenService;

    @InjectMocks
    private IdentityPrincipalService identityPrincipalService;

    @Test
    void requireActiveUserReturnsUserWhenTokenVersionMatches() {
        IdentityUser user = identityUser(18L, IdentityUserRole.USER, IdentityUserStatus.ACTIVE, 2L);

        when(identityTokenService.parseToken("token"))
                .thenReturn(new IdentityTokenService.TokenClaims(18L, 2L, 4_200_000_000L));
        when(identityUserRepository.findById(18L)).thenReturn(Optional.of(user));

        IdentityUser response = identityPrincipalService.requireActiveUser("Bearer token");

        assertThat(response).isSameAs(user);
    }

    @Test
    void requireActiveUserChecksRefreshSessionWhenTokenCarriesSessionId() {
        IdentityUser user = identityUser(18L, IdentityUserRole.USER, IdentityUserStatus.ACTIVE, 2L);

        when(identityTokenService.parseToken("token"))
                .thenReturn(new IdentityTokenService.TokenClaims(18L, 2L, 51L, 4_200_000_000L));
        when(identityUserRepository.findById(18L)).thenReturn(Optional.of(user));

        IdentityUser response = identityPrincipalService.requireActiveUser("Bearer token");

        assertThat(response).isSameAs(user);
        verify(identityRefreshTokenService).requireActiveSession(51L, user);
    }

    @Test
    void requireActiveUserRejectsMissingBearerToken() {
        assertThatThrownBy(() -> identityPrincipalService.requireActiveUser(null))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("请先登录。");
    }

    @Test
    void requireActiveUserRejectsStaleTokenVersion() {
        IdentityUser user = identityUser(18L, IdentityUserRole.USER, IdentityUserStatus.ACTIVE, 2L);

        when(identityTokenService.parseToken("token"))
                .thenReturn(new IdentityTokenService.TokenClaims(18L, 1L, 4_200_000_000L));
        when(identityUserRepository.findById(18L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> identityPrincipalService.requireActiveUser("Bearer token"))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("登录状态已失效。");
    }

    @Test
    void requireAdminUserRejectsRegularUser() {
        IdentityUser user = identityUser(18L, IdentityUserRole.USER, IdentityUserStatus.ACTIVE, 2L);

        when(identityTokenService.parseToken("token"))
                .thenReturn(new IdentityTokenService.TokenClaims(18L, 2L, 4_200_000_000L));
        when(identityUserRepository.findById(18L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> identityPrincipalService.requireAdminUser("Bearer token"))
                .isInstanceOf(IdentityAuthException.class)
                .hasMessage("当前接口仅允许管理员访问。");
    }

    private IdentityUser identityUser(
            Long id,
            IdentityUserRole role,
            IdentityUserStatus status,
            Long tokenVersion
    ) {
        IdentityUser user = newIdentityUser();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "phoneNumber", "13800000000");
        ReflectionTestUtils.setField(user, "email", "email-user@example.com");
        ReflectionTestUtils.setField(user, "emailVerifiedAt", LocalDateTime.of(2026, 8, 17, 15, 30));
        ReflectionTestUtils.setField(user, "nickname", "Alicia");
        ReflectionTestUtils.setField(user, "avatarUrl", "cos:user-avatars/18/avatar.webp");
        ReflectionTestUtils.setField(user, "passwordHash", "hash");
        ReflectionTestUtils.setField(user, "tokenVersion", tokenVersion);
        ReflectionTestUtils.setField(user, "role", role);
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
