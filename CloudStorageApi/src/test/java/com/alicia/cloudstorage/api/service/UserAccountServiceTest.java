package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.auth.TokenService;
import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private CosFileStorageService cosFileStorageService;

    @Mock
    private CloudUserProfileService cloudUserProfileService;

    @InjectMocks
    private UserAccountService userAccountService;

    @Test
    void loginAcceptsEmailIdentifier() {
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", 18L);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 8, 17, 10, 0));
        user.setPhoneNumber(null);
        user.setEmail("email-user@example.com");
        user.setNickname("Email User");
        user.setPasswordHash("hash");
        user.setTokenVersion(0L);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setStorageQuotaBytes(4096L);

        UserProfileResponse profile = profile(user, 4096L, 1024L, 3072L);

        when(sysUserRepository.findByEmail("email-user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd", "hash")).thenReturn(true);
        when(tokenService.createToken(user)).thenReturn("token");
        when(cloudUserProfileService.toUserProfile(user)).thenReturn(profile);

        var response = userAccountService.login(new LoginRequest("Email-User@Example.COM", null, null, "Passw0rd"));

        assertThat(response.token()).isEqualTo("token");
        assertThat(response.user().phoneNumber()).isEmpty();
        assertThat(response.user().email()).isEqualTo("email-user@example.com");
    }

    @Test
    void getCurrentUserDelegatesToCloudProfileService() {
        UserProfileResponse profile = new UserProfileResponse(
                7L,
                "13800000007",
                null,
                "Current User",
                null,
                null,
                "USER",
                "ACTIVE",
                LocalDateTime.of(2026, 8, 17, 11, 0),
                1024L,
                0L,
                1024L
        );

        when(cloudUserProfileService.getCurrentUser(7L)).thenReturn(profile);

        assertThat(userAccountService.getCurrentUser(7L)).isSameAs(profile);
    }

    @Test
    void createVerifiedEmailUserPersistsActiveUserAndReturnsLoginSession() {
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setStorageQuotaBytes(2048L);
            return null;
        }).when(cloudUserProfileService).assignDefaultStorageQuota(any(SysUser.class));

        when(sysUserRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd")).thenReturn("hash");
        when(sysUserRepository.save(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 72L);
            ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 8, 17, 10, 30));
            return user;
        });
        when(tokenService.createToken(any(SysUser.class))).thenReturn("new-token");
        when(cloudUserProfileService.toUserProfile(any(SysUser.class)))
                .thenAnswer(invocation -> profile(invocation.getArgument(0), 2048L, 0L, 2048L));

        var response = userAccountService.createVerifiedEmailUser("New@Example.COM", "New User", "Passw0rd");

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserRepository).save(userCaptor.capture());
        SysUser savedUser = userCaptor.getValue();
        assertThat(savedUser.getPhoneNumber()).isNull();
        assertThat(savedUser.getEmail()).isEqualTo("new@example.com");
        assertThat(savedUser.getEmailVerifiedAt()).isNotNull();
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getStorageQuotaBytes()).isEqualTo(2048L);
        assertThat(response.token()).isEqualTo("new-token");
        assertThat(response.user().email()).isEqualTo("new@example.com");
    }

    @Test
    void createAdminUserDelegatesInitialQuotaAndReturnsUnlimitedProfile() {
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setStorageQuotaBytes(512L * 1024L * 1024L);
            return null;
        }).when(cloudUserProfileService).assignInitialStorageQuota(
                any(SysUser.class),
                eq(UserRole.ADMIN),
                eq(null)
        );
        when(passwordEncoder.encode("Admin@123")).thenReturn("hashed-password");
        when(sysUserRepository.save(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 55L);
            ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 4, 29, 16, 0));
            return user;
        });
        when(cloudUserProfileService.inheritAdminHomeBackground(eq(1L), eq(false), any(SysUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(cloudUserProfileService.toUserProfile(any(SysUser.class)))
                .thenAnswer(invocation -> profile(invocation.getArgument(0), null, 1024L, null));

        var response = userAccountService.createUser(
                1L,
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
        assertThat(userCaptor.getValue().getStorageQuotaBytes()).isEqualTo(512L * 1024L * 1024L);
        assertThat(userCaptor.getValue().getTokenVersion()).isZero();
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.storageQuotaBytes()).isNull();
        assertThat(response.usedBytes()).isEqualTo(1024L);
        assertThat(response.remainingBytes()).isNull();
    }

    @Test
    void changePasswordInvalidatesExistingTokens() {
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", 35L);
        user.setPhoneNumber("13800000035");
        user.setNickname("Admin User");
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("old-hash");
        user.setTokenVersion(4L);

        when(sysUserRepository.findById(35L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass@123", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass@123")).thenReturn("new-hash");
        when(sysUserRepository.save(any(SysUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userAccountService.changePassword(35L, new ChangePasswordRequest("OldPass@123", "NewPass@123"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getTokenVersion()).isEqualTo(5L);
        verify(sysUserRepository).save(user);
    }

    @Test
    void resetUserPasswordInvalidatesExistingTokens() {
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", 64L);
        user.setPhoneNumber("13800000064");
        user.setNickname("Reset User");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("current-hash");
        user.setTokenVersion(2L);

        when(sysUserRepository.findById(64L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("ResetPass@456", "current-hash")).thenReturn(false);
        when(passwordEncoder.encode("ResetPass@456")).thenReturn("reset-hash");
        when(sysUserRepository.save(any(SysUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userAccountService.resetUserPassword(1L, 64L, new AdminResetUserPasswordRequest("ResetPass@456"));

        assertThat(user.getPasswordHash()).isEqualTo("reset-hash");
        assertThat(user.getTokenVersion()).isEqualTo(3L);
        verify(sysUserRepository).save(user);
    }

    @Test
    void resetUserPasswordRejectsResettingCurrentAdmin() {
        assertThatThrownBy(() -> userAccountService.resetUserPassword(5L, 5L, new AdminResetUserPasswordRequest("ResetPass@456")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UserProfileResponse profile(SysUser user, Long storageQuotaBytes, long usedBytes, Long remainingBytes) {
        return new UserProfileResponse(
                user.getId(),
                user.getPhoneNumber() == null ? "" : user.getPhoneNumber(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getHomeBackgroundUrl(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getCreatedAt(),
                storageQuotaBytes,
                usedBytes,
                remainingBytes
        );
    }
}
