package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityAdminUserServiceTest {

    @Mock
    private IdentityPrincipalService identityPrincipalService;

    @Mock
    private IdentityUserRepository identityUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private IdentityAdminUserService identityAdminUserService;

    @Test
    void resetUserPasswordUpdatesTargetPasswordAndInvalidatesTokens() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN, 0L);
        IdentityUser target = identityUser(64L, IdentityUserRole.USER, 2L);
        target.setPasswordHash("current-hash");

        when(identityPrincipalService.requireAdminUser("Bearer admin-token")).thenReturn(admin);
        when(identityUserRepository.findById(64L)).thenReturn(Optional.of(target));
        when(passwordEncoder.matches("ResetPass1", "current-hash")).thenReturn(false);
        when(passwordEncoder.encode("ResetPass1")).thenReturn("reset-hash");

        identityAdminUserService.resetUserPassword(
                "Bearer admin-token",
                64L,
                new AdminResetUserPasswordRequest("ResetPass1")
        );

        assertThat(target.getPasswordHash()).isEqualTo("reset-hash");
        assertThat(target.getTokenVersion()).isEqualTo(3L);
        verify(identityUserRepository).save(target);
    }

    @Test
    void resetUserPasswordRejectsResettingCurrentAdmin() {
        IdentityUser admin = identityUser(5L, IdentityUserRole.ADMIN, 0L);

        when(identityPrincipalService.requireAdminUser("Bearer admin-token")).thenReturn(admin);

        assertThatThrownBy(() -> identityAdminUserService.resetUserPassword(
                "Bearer admin-token",
                5L,
                new AdminResetUserPasswordRequest("ResetPass1")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前接口仅用于重置其他用户密码，请使用修改密码功能。");

        verify(identityUserRepository, never()).findById(5L);
    }

    @Test
    void resetUserPasswordRejectsShortNewPassword() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN, 0L);
        IdentityUser target = identityUser(64L, IdentityUserRole.USER, 2L);

        when(identityPrincipalService.requireAdminUser("Bearer admin-token")).thenReturn(admin);
        when(identityUserRepository.findById(64L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> identityAdminUserService.resetUserPassword(
                "Bearer admin-token",
                64L,
                new AdminResetUserPasswordRequest("short")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("新密码长度至少为 6 位。");

        verify(identityUserRepository, never()).save(target);
    }

    @Test
    void resetUserPasswordRejectsSameCurrentPassword() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN, 0L);
        IdentityUser target = identityUser(64L, IdentityUserRole.USER, 2L);
        target.setPasswordHash("current-hash");

        when(identityPrincipalService.requireAdminUser("Bearer admin-token")).thenReturn(admin);
        when(identityUserRepository.findById(64L)).thenReturn(Optional.of(target));
        when(passwordEncoder.matches("ResetPass1", "current-hash")).thenReturn(true);

        assertThatThrownBy(() -> identityAdminUserService.resetUserPassword(
                "Bearer admin-token",
                64L,
                new AdminResetUserPasswordRequest("ResetPass1")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("新密码不能与当前密码相同。");

        verify(identityUserRepository, never()).save(target);
    }

    private IdentityUser identityUser(Long id, IdentityUserRole role, Long tokenVersion) {
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
        ReflectionTestUtils.setField(user, "status", IdentityUserStatus.ACTIVE);
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
