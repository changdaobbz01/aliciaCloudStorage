package com.alicia.cloudstorage.api.auth;

import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.identity.UserStatus;
import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentPrincipalServiceTest {

    @Mock
    private IdentityAuthGateway identityAuthGateway;

    @InjectMocks
    private CurrentPrincipalService currentPrincipalService;

    @Test
    void requirePrincipalDelegatesToIdentityApiAndReturnsLightweightCurrentUser() {
        IdentityUserSnapshot account = account(22L, UserRole.ADMIN);

        when(identityAuthGateway.me("Bearer token")).thenReturn(account);

        CurrentPrincipal principal = currentPrincipalService.requirePrincipal("Bearer token");

        assertThat(principal.userId()).isEqualTo(22L);
        assertThat(principal.isAdmin()).isTrue();
        verify(identityAuthGateway).me("Bearer token");
    }

    @Test
    void requireAdminPrincipalRejectsRegularUsers() {
        when(identityAuthGateway.me("Bearer token")).thenReturn(account(28L, UserRole.USER));

        assertThatThrownBy(() -> currentPrincipalService.requireAdminPrincipal("Bearer token"))
                .isInstanceOf(AuthException.class)
                .hasMessage("当前接口仅允许管理员访问。");
    }

    @Test
    void requireAdminPrincipalAcceptsAdminUsers() {
        when(identityAuthGateway.me("Bearer token")).thenReturn(account(1L, UserRole.ADMIN));

        CurrentPrincipal principal = currentPrincipalService.requireAdminPrincipal("Bearer token");

        assertThat(principal.userId()).isEqualTo(1L);
        assertThat(principal.isAdmin()).isTrue();
    }

    @Test
    void identityAuthFailureIsPropagated() {
        when(identityAuthGateway.me("Bearer stale-token"))
                .thenThrow(new AuthException("登录状态已失效。"));

        assertThatThrownBy(() -> currentPrincipalService.requirePrincipal("Bearer stale-token"))
                .isInstanceOf(AuthException.class)
                .hasMessage("登录状态已失效。");
    }

    private IdentityUserSnapshot account(Long id, UserRole role) {
        return new IdentityUserSnapshot(
                id,
                "13900000000",
                "user@example.com",
                "Alicia",
                null,
                role,
                UserStatus.ACTIVE,
                LocalDateTime.of(2026, 4, 29, 15, 30)
        );
    }
}
