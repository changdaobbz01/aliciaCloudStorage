package com.alicia.cloudstorage.api.auth;

import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import com.alicia.cloudstorage.api.service.CloudUserProfileProvisioningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private CloudUserProfileProvisioningService cloudUserProfileProvisioningService;

    @InjectMocks
    private AuthService authService;

    @Test
    void requireUserRejectsStaleTokenVersion() {
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", 12L);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(3L);

        when(tokenService.parseToken("token")).thenReturn(new TokenService.TokenClaims(12L, 2L, 4_200_000_000L));
        when(sysUserRepository.findById(12L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.requireUser("Bearer token"))
                .isInstanceOf(AuthException.class);

        verify(cloudUserProfileProvisioningService, never()).ensureCloudProfile(user);
    }

    @Test
    void requireUserAcceptsMatchingTokenVersion() {
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", 18L);
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(5L);

        when(tokenService.parseToken("token")).thenReturn(new TokenService.TokenClaims(18L, 5L, 4_200_000_000L));
        when(sysUserRepository.findById(18L)).thenReturn(Optional.of(user));

        assertThat(authService.requireUser("Bearer token")).isSameAs(user);
        verify(cloudUserProfileProvisioningService).ensureCloudProfile(user);
    }

    @Test
    void requirePrincipalReturnsLightweightCurrentUser() {
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", 22L);
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(1L);

        when(tokenService.parseToken("token")).thenReturn(new TokenService.TokenClaims(22L, 1L, 4_200_000_000L));
        when(sysUserRepository.findById(22L)).thenReturn(Optional.of(user));

        CurrentPrincipal principal = authService.requirePrincipal("Bearer token");

        assertThat(principal.userId()).isEqualTo(22L);
        assertThat(principal.isAdmin()).isTrue();
    }

    @Test
    void requireAdminPrincipalRejectsRegularUsers() {
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", 28L);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(1L);

        when(tokenService.parseToken("token")).thenReturn(new TokenService.TokenClaims(28L, 1L, 4_200_000_000L));
        when(sysUserRepository.findById(28L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.requireAdminPrincipal("Bearer token"))
                .isInstanceOf(AuthException.class);
    }
}
