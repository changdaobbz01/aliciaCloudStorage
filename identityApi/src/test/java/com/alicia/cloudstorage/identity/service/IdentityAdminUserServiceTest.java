package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.AdminCreateIdentityUserRequest;
import com.alicia.cloudstorage.identity.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
    private IdentityCredentialService identityCredentialService;

    @Spy
    private IdentityUserInputNormalizer identityUserInputNormalizer = new IdentityUserInputNormalizer();

    @InjectMocks
    private IdentityAdminUserService identityAdminUserService;

    @Test
    void listUsersRequiresAdminAndReturnsIdentityUsers() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN, 0L);
        IdentityUser firstUser = identityUser(1L, IdentityUserRole.ADMIN, 0L);
        IdentityUser secondUser = identityUser(2L, IdentityUserRole.USER, 0L);
        secondUser.setEmail("second@example.com");
        secondUser.setNickname("Second");

        when(identityPrincipalService.requireAdminUser("Bearer admin-token")).thenReturn(admin);
        when(identityUserRepository.findAllByOrderByIdAsc()).thenReturn(List.of(firstUser, secondUser));

        var response = identityAdminUserService.listUsers("Bearer admin-token");

        assertThat(response).hasSize(2);
        assertThat(response.get(0).id()).isEqualTo(1L);
        assertThat(response.get(1).email()).isEqualTo("second@example.com");
        verify(identityPrincipalService).requireAdminUser("Bearer admin-token");
    }

    @Test
    void createUserPersistsOnlyIdentityFields() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN, 0L);
        AdminCreateIdentityUserRequest request = new AdminCreateIdentityUserRequest(
                "13800000001",
                null,
                "New User",
                "https://example.com/avatar.png",
                "Passw0rd",
                "USER"
        );

        when(identityPrincipalService.requireAdminUser("Bearer admin-token")).thenReturn(admin);
        when(identityCredentialService.encodeInitialPassword("Passw0rd")).thenReturn("password-hash");
        when(identityUserRepository.existsByPhoneNumber("13800000001")).thenReturn(false);
        when(identityUserRepository.save(any(IdentityUser.class))).thenAnswer(invocation -> {
            IdentityUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 72L);
            ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 8, 19, 9, 30));
            return user;
        });

        var response = identityAdminUserService.createUser("Bearer admin-token", request);

        ArgumentCaptor<IdentityUser> userCaptor = ArgumentCaptor.forClass(IdentityUser.class);
        verify(identityUserRepository).save(userCaptor.capture());
        IdentityUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getPhoneNumber()).isEqualTo("13800000001");
        assertThat(savedUser.getEmail()).isNull();
        assertThat(savedUser.getEmailVerifiedAt()).isNull();
        assertThat(savedUser.getNickname()).isEqualTo("New User");
        assertThat(savedUser.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(savedUser.getPasswordHash()).isEqualTo("password-hash");
        assertThat(savedUser.getTokenVersion()).isZero();
        assertThat(savedUser.getRole()).isEqualTo(IdentityUserRole.USER);
        assertThat(savedUser.getStatus()).isEqualTo(IdentityUserStatus.ACTIVE);
        assertThat(response.id()).isEqualTo(72L);
    }

    @Test
    void createUserAcceptsEmailIdentifierAndNormalizesIt() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN, 0L);
        AdminCreateIdentityUserRequest request = new AdminCreateIdentityUserRequest(
                null,
                "NewUser@Example.COM",
                "New User",
                null,
                "Passw0rd",
                "ADMIN"
        );

        when(identityPrincipalService.requireAdminUser("Bearer admin-token")).thenReturn(admin);
        when(identityCredentialService.encodeInitialPassword("Passw0rd")).thenReturn("password-hash");
        when(identityUserRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(identityUserRepository.save(any(IdentityUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        identityAdminUserService.createUser("Bearer admin-token", request);

        ArgumentCaptor<IdentityUser> userCaptor = ArgumentCaptor.forClass(IdentityUser.class);
        verify(identityUserRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("newuser@example.com");
        assertThat(userCaptor.getValue().getRole()).isEqualTo(IdentityUserRole.ADMIN);
    }

    @Test
    void createUserRejectsMissingLoginIdentifier() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN, 0L);

        when(identityPrincipalService.requireAdminUser("Bearer admin-token")).thenReturn(admin);

        assertThatThrownBy(() -> identityAdminUserService.createUser(
                "Bearer admin-token",
                new AdminCreateIdentityUserRequest(null, null, "New User", null, "Passw0rd", "USER")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("手机号或邮箱不能为空。");
    }

    @Test
    void createUserRejectsDuplicatePhoneNumber() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN, 0L);

        when(identityPrincipalService.requireAdminUser("Bearer admin-token")).thenReturn(admin);
        when(identityUserRepository.existsByPhoneNumber("13800000001")).thenReturn(true);

        assertThatThrownBy(() -> identityAdminUserService.createUser(
                "Bearer admin-token",
                new AdminCreateIdentityUserRequest("13800000001", null, "New User", null, "Passw0rd", "USER")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("手机号已被其他账户使用。");

        verify(identityUserRepository, never()).save(any());
    }

    @Test
    void createUserRejectsInvalidRole() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN, 0L);

        when(identityPrincipalService.requireAdminUser("Bearer admin-token")).thenReturn(admin);

        assertThatThrownBy(() -> identityAdminUserService.createUser(
                "Bearer admin-token",
                new AdminCreateIdentityUserRequest("13800000001", null, "New User", null, "Passw0rd", "OWNER")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("角色只能是 ADMIN 或 USER。");
    }

    @Test
    void resetUserPasswordUpdatesTargetPasswordAndInvalidatesTokens() {
        IdentityUser admin = identityUser(1L, IdentityUserRole.ADMIN, 0L);
        IdentityUser target = identityUser(64L, IdentityUserRole.USER, 2L);
        target.setPasswordHash("current-hash");

        when(identityPrincipalService.requireAdminUser("Bearer admin-token")).thenReturn(admin);
        when(identityUserRepository.findById(64L)).thenReturn(Optional.of(target));
        doAnswer(invocation -> {
            target.setPasswordHash("reset-hash");
            target.setTokenVersion(3L);
            return null;
        }).when(identityCredentialService).resetPassword(target, "ResetPass1");

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
        doThrow(new IllegalArgumentException("新密码长度至少为 6 位。"))
                .when(identityCredentialService)
                .resetPassword(target, "short");

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
        doThrow(new IllegalArgumentException("新密码不能与当前密码相同。"))
                .when(identityCredentialService)
                .resetPassword(target, "ResetPass1");

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
