package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.auth.TokenService;
import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminUpdateUserQuotaRequest;
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
    private StorageQuotaService storageQuotaService;

    @InjectMocks
    private UserAccountService userAccountService;

    @Test
    void createAdminUserUsesDefaultQuotaAndReturnsUnlimitedProfile() {
        long defaultQuotaBytes = 512L * 1024L * 1024L;

        when(storageQuotaService.getDefaultUserQuotaBytes()).thenReturn(defaultQuotaBytes);
        when(passwordEncoder.encode("Admin@123")).thenReturn("hashed-password");
        when(sysUserRepository.save(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 55L);
            ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 4, 29, 16, 0));
            return user;
        });
        when(storageQuotaService.getUsedBytes(55L)).thenReturn(1024L);

        var response = userAccountService.createUser(
                new AdminCreateUserRequest(
                        "13800000001",
                        "配额管理员",
                        null,
                        "Admin@123",
                        "ADMIN",
                        null
                )
        );

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getStorageQuotaBytes()).isEqualTo(defaultQuotaBytes);
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.storageQuotaBytes()).isNull();
        assertThat(response.usedBytes()).isEqualTo(1024L);
        assertThat(response.remainingBytes()).isNull();
    }

    @Test
    void updateUserQuotaPersistsNewQuota() {
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", 77L);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 4, 29, 15, 30));
        user.setPhoneNumber("13900000000");
        user.setNickname("Alicia");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setStorageQuotaBytes(1024L);

        when(sysUserRepository.findById(77L)).thenReturn(Optional.of(user));
        when(storageQuotaService.normalizeQuotaBytes(4096L, "用户最大存储额度")).thenReturn(4096L);
        when(storageQuotaService.getUsedBytes(77L)).thenReturn(1536L);
        when(sysUserRepository.save(any(SysUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userAccountService.updateUserStorageQuota(77L, new AdminUpdateUserQuotaRequest(4096L));

        verify(storageQuotaService).validateQuotaAssignment(77L, 4096L);
        assertThat(user.getStorageQuotaBytes()).isEqualTo(4096L);
        assertThat(response.storageQuotaBytes()).isEqualTo(4096L);
        assertThat(response.usedBytes()).isEqualTo(1536L);
        assertThat(response.remainingBytes()).isEqualTo(2560L);
    }

    @Test
    void updateUserQuotaRejectsAdminAccounts() {
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", 91L);
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        user.setStorageQuotaBytes(1024L);

        when(sysUserRepository.findById(91L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userAccountService.updateUserStorageQuota(91L, new AdminUpdateUserQuotaRequest(4096L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("管理员账号不限制存储额度");
    }

    @Test
    void uploadCurrentUserHomeBackgroundPersistsCosReference() {
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", 23L);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 4, 29, 18, 0));
        user.setPhoneNumber("13800000023");
        user.setNickname("背景用户");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setStorageQuotaBytes(2048L);
        user.setHomeBackgroundUrl("cosbg:user-home-backgrounds/23/old.webp");

        MockMultipartFile file = new MockMultipartFile("file", "bg.webp", "image/webp", new byte[]{1, 2, 3});

        when(sysUserRepository.findById(23L)).thenReturn(Optional.of(user));
        when(cosFileStorageService.uploadUserHomeBackground(23L, file))
                .thenReturn(new CosFileStorageService.StoredCosFile("user-home-backgrounds/23/new.webp", "image/webp", 3L));
        when(sysUserRepository.save(any(SysUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageQuotaService.getUsedBytes(23L)).thenReturn(512L);

        var response = userAccountService.uploadCurrentUserHomeBackground(23L, file);

        verify(cosFileStorageService).deleteObjectQuietly("user-home-backgrounds/23/old.webp");
        assertThat(user.getHomeBackgroundUrl()).isEqualTo("cosbg:user-home-backgrounds/23/new.webp");
        assertThat(response.homeBackgroundUrl()).isEqualTo("cosbg:user-home-backgrounds/23/new.webp");
    }
}
