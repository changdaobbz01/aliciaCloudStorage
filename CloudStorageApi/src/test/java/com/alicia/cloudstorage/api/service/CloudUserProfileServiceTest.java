package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AdminUpdateUserQuotaRequest;
import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudUserProfileServiceTest {

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private CosFileStorageService cosFileStorageService;

    @Mock
    private StorageQuotaService storageQuotaService;

    @InjectMocks
    private CloudUserProfileService cloudUserProfileService;

    @Test
    void updateUserQuotaPersistsNewQuota() {
        SysUser user = regularUser(77L, 1024L);

        when(sysUserRepository.findById(77L)).thenReturn(Optional.of(user));
        when(storageQuotaService.normalizeQuotaBytes(eq(4096L), anyString())).thenReturn(4096L);
        when(storageQuotaService.getUsedBytes(77L)).thenReturn(1536L);
        when(sysUserRepository.save(any(SysUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = cloudUserProfileService.updateUserStorageQuota(77L, new AdminUpdateUserQuotaRequest(4096L));

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

        assertThatThrownBy(() -> cloudUserProfileService.updateUserStorageQuota(91L, new AdminUpdateUserQuotaRequest(4096L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uploadCurrentUserHomeBackgroundPersistsCosReference() {
        SysUser user = regularUser(23L, 2048L);
        user.setHomeBackgroundUrl("cosbg:user-home-backgrounds/23/old.webp");

        MockMultipartFile file = new MockMultipartFile("file", "bg.webp", "image/webp", new byte[]{1, 2, 3});

        when(sysUserRepository.findById(23L)).thenReturn(Optional.of(user));
        when(cosFileStorageService.uploadUserHomeBackground(23L, file))
                .thenReturn(new CosFileStorageService.StoredCosFile("user-home-backgrounds/23/new.webp", "image/webp", 3L));
        when(sysUserRepository.save(any(SysUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageQuotaService.getUsedBytes(23L)).thenReturn(512L);

        var response = cloudUserProfileService.uploadCurrentUserHomeBackground(23L, file);

        verify(cosFileStorageService).deleteObjectQuietly("user-home-backgrounds/23/old.webp");
        assertThat(user.getHomeBackgroundUrl()).isEqualTo("cosbg:user-home-backgrounds/23/new.webp");
        assertThat(response.homeBackgroundUrl()).isEqualTo("cosbg:user-home-backgrounds/23/new.webp");
    }

    @Test
    void inheritAdminHomeBackgroundDuplicatesLocalCosReference() {
        SysUser admin = new SysUser();
        ReflectionTestUtils.setField(admin, "id", 1L);
        admin.setPhoneNumber("13800000001");
        admin.setNickname("Admin");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setStorageQuotaBytes(2048L);
        admin.setHomeBackgroundUrl("cosbg:user-home-backgrounds/1/source.webp");
        SysUser targetUser = regularUser(23L, 2048L);

        when(sysUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(cosFileStorageService.duplicateUserHomeBackground(23L, "user-home-backgrounds/1/source.webp"))
                .thenReturn(new CosFileStorageService.StoredCosFile("user-home-backgrounds/23/copied.webp", "image/webp", 3L));
        when(sysUserRepository.save(targetUser)).thenAnswer(invocation -> invocation.getArgument(0));

        SysUser result = cloudUserProfileService.inheritAdminHomeBackground(1L, true, targetUser);

        verify(sysUserRepository).save(targetUser);
        assertThat(result).isSameAs(targetUser);
        assertThat(targetUser.getHomeBackgroundUrl()).isEqualTo("cosbg:user-home-backgrounds/23/copied.webp");
    }

    @Test
    void assignInitialStorageQuotaKeepsRoleRulesInCloudProfileBoundary() {
        SysUser admin = new SysUser();
        SysUser user = new SysUser();

        when(storageQuotaService.getDefaultUserQuotaBytes()).thenReturn(2048L);
        when(storageQuotaService.normalizeQuotaBytes(4096L, "用户最大存储额度")).thenReturn(4096L);

        cloudUserProfileService.assignInitialStorageQuota(admin, UserRole.ADMIN, null);
        cloudUserProfileService.assignInitialStorageQuota(user, UserRole.USER, 4096L);

        assertThat(admin.getStorageQuotaBytes()).isEqualTo(2048L);
        assertThat(user.getStorageQuotaBytes()).isEqualTo(4096L);
    }

    private SysUser regularUser(Long id, Long storageQuotaBytes) {
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 4, 29, 15, 30));
        user.setPhoneNumber("13900000000");
        user.setNickname("Alicia");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setStorageQuotaBytes(storageQuotaBytes);
        return user;
    }
}
