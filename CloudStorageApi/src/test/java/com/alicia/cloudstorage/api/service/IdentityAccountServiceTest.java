package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.auth.TokenService;
import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityAccountServiceTest {

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private CosFileStorageService cosFileStorageService;

    @InjectMocks
    private IdentityAccountService identityAccountService;

    @Test
    void loginAcceptsEmailIdentifier() {
        SysUser user = regularUser(18L, UserRole.USER, 4096L);
        user.setPhoneNumber(null);
        user.setEmail("email-user@example.com");
        user.setPasswordHash("hash");

        when(sysUserRepository.findByEmail("email-user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd", "hash")).thenReturn(true);
        when(tokenService.createToken(user)).thenReturn("token");

        IdentityLoginSession response =
                identityAccountService.login(new LoginRequest("Email-User@Example.COM", null, null, "Passw0rd"));

        assertThat(response.token()).isEqualTo("token");
        assertThat(response.account().phoneNumber()).isNull();
        assertThat(response.account().email()).isEqualTo("email-user@example.com");
    }

    @Test
    void createVerifiedEmailUserPersistsActiveUserAndReturnsLoginSession() {
        when(sysUserRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd")).thenReturn("hash");
        when(sysUserRepository.save(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 72L);
            ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 8, 17, 10, 30));
            return user;
        });
        when(tokenService.createToken(any(SysUser.class))).thenReturn("new-token");

        IdentityLoginSession response =
                identityAccountService.createVerifiedEmailUser("New@Example.COM", "New User", "Passw0rd");

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserRepository).save(userCaptor.capture());
        SysUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getPhoneNumber()).isNull();
        assertThat(savedUser.getEmail()).isEqualTo("new@example.com");
        assertThat(savedUser.getEmailVerifiedAt()).isNotNull();
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getStorageQuotaBytes()).isNull();
        assertThat(response.token()).isEqualTo("new-token");
        assertThat(response.account().email()).isEqualTo("new@example.com");
    }

    @Test
    void createAdminUserPersistsOnlyIdentityFields() {
        when(passwordEncoder.encode("Admin@123")).thenReturn("hashed-password");
        when(sysUserRepository.save(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 55L);
            ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 4, 29, 16, 0));
            return user;
        });

        IdentityAccount response = identityAccountService.createUser(
                new AdminCreateUserRequest(
                        "13800000001",
                        "Quota Admin",
                        null,
                        false,
                        "Admin@123",
                        "ADMIN",
                        null
                )
        );

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getStorageQuotaBytes()).isNull();
        assertThat(userCaptor.getValue().getTokenVersion()).isZero();
        assertThat(response.role()).isEqualTo(UserRole.ADMIN);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void uploadCurrentUserAvatarPersistsCosReferenceAndDeletesOldLocalAvatar() {
        SysUser user = regularUser(23L, UserRole.USER, 2048L);
        user.setAvatarUrl("cos:user-avatars/23/old.webp");

        MockMultipartFile file = new MockMultipartFile("file", "avatar.webp", "image/webp", new byte[]{1, 2, 3});

        when(sysUserRepository.findById(23L)).thenReturn(Optional.of(user));
        when(cosFileStorageService.uploadUserAvatar(23L, file))
                .thenReturn(new CosFileStorageService.StoredCosFile("user-avatars/23/new.webp", "image/webp", 3L));
        when(sysUserRepository.save(any(SysUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IdentityAccount response = identityAccountService.uploadCurrentUserAvatar(23L, file);

        verify(cosFileStorageService).deleteObjectQuietly("user-avatars/23/old.webp");
        assertThat(user.getAvatarUrl()).isEqualTo("cos:user-avatars/23/new.webp");
        assertThat(response.avatarUrl()).isEqualTo("cos:user-avatars/23/new.webp");
    }

    @Test
    void changePasswordInvalidatesExistingTokens() {
        SysUser user = regularUser(35L, UserRole.ADMIN, 2048L);
        user.setPasswordHash("old-hash");
        user.setTokenVersion(4L);

        when(sysUserRepository.findById(35L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass@123", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass@123")).thenReturn("new-hash");
        when(sysUserRepository.save(any(SysUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        identityAccountService.changePassword(35L, new ChangePasswordRequest("OldPass@123", "NewPass@123"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getTokenVersion()).isEqualTo(5L);
        verify(sysUserRepository).save(user);
    }

    @Test
    void resetUserPasswordInvalidatesExistingTokens() {
        SysUser user = regularUser(64L, UserRole.USER, 2048L);
        user.setPasswordHash("current-hash");
        user.setTokenVersion(2L);

        when(sysUserRepository.findById(64L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("ResetPass@456", "current-hash")).thenReturn(false);
        when(passwordEncoder.encode("ResetPass@456")).thenReturn("reset-hash");
        when(sysUserRepository.save(any(SysUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        identityAccountService.resetUserPassword(1L, 64L, new AdminResetUserPasswordRequest("ResetPass@456"));

        assertThat(user.getPasswordHash()).isEqualTo("reset-hash");
        assertThat(user.getTokenVersion()).isEqualTo(3L);
        verify(sysUserRepository).save(user);
    }

    @Test
    void resetUserPasswordRejectsResettingCurrentAdmin() {
        assertThatThrownBy(() -> identityAccountService.resetUserPassword(5L, 5L, new AdminResetUserPasswordRequest("ResetPass@456")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SysUser regularUser(Long id, UserRole role, Long storageQuotaBytes) {
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 4, 29, 15, 30));
        user.setPhoneNumber("13900000000");
        user.setEmail("user@example.com");
        user.setNickname("Alicia");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(0L);
        user.setStorageQuotaBytes(storageQuotaBytes);
        return user;
    }
}
