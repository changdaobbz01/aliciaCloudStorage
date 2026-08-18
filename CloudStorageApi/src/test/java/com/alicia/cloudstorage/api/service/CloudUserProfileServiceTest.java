package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AdminUpdateUserQuotaRequest;
import com.alicia.cloudstorage.api.entity.CloudUserProfileEntity;
import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.repository.CloudUserProfileRepository;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private CloudUserProfileRepository cloudUserProfileRepository;

    @Mock
    private CosFileStorageService cosFileStorageService;

    @Mock
    private StorageQuotaService storageQuotaService;

    private CloudUserProfileService cloudUserProfileService;

    @BeforeEach
    void setUp() {
        CloudUserProfileProvisioningService provisioningService =
                new CloudUserProfileProvisioningService(cloudUserProfileRepository, 2048L);
        cloudUserProfileService = new CloudUserProfileService(
                sysUserRepository,
                cloudUserProfileRepository,
                cosFileStorageService,
                storageQuotaService,
                provisioningService
        );
    }

    @Test
    void updateUserQuotaPersistsNewQuota() {
        SysUser user = regularUser(77L, 1024L);
        CloudUserProfileEntity profile = cloudProfile(77L, null, 1024L);

        when(sysUserRepository.findById(77L)).thenReturn(Optional.of(user));
        when(cloudUserProfileRepository.findById(77L)).thenReturn(Optional.of(profile));
        when(storageQuotaService.normalizeQuotaBytes(eq(4096L), anyString())).thenReturn(4096L);
        when(cloudUserProfileRepository.save(any(CloudUserProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = cloudUserProfileService.updateUserStorageQuota(77L, new AdminUpdateUserQuotaRequest(4096L));

        verify(storageQuotaService).validateQuotaAssignment(77L, 4096L);
        assertThat(profile.getStorageQuotaBytes()).isEqualTo(4096L);
        assertThat(response.storageQuotaBytes()).isEqualTo(4096L);
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
        CloudUserProfileEntity profile =
                cloudProfile(23L, "cosbg:user-home-backgrounds/23/old.webp", 2048L);

        MockMultipartFile file = new MockMultipartFile("file", "bg.webp", "image/webp", new byte[]{1, 2, 3});

        when(sysUserRepository.findById(23L)).thenReturn(Optional.of(user));
        when(cloudUserProfileRepository.findById(23L)).thenReturn(Optional.of(profile));
        when(cosFileStorageService.uploadUserHomeBackground(23L, file))
                .thenReturn(new CosFileStorageService.StoredCosFile("user-home-backgrounds/23/new.webp", "image/webp", 3L));
        when(cloudUserProfileRepository.save(any(CloudUserProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = cloudUserProfileService.uploadCurrentUserHomeBackground(23L, file);

        verify(cosFileStorageService).deleteObjectQuietly("user-home-backgrounds/23/old.webp");
        assertThat(profile.getHomeBackgroundUrl()).isEqualTo("cosbg:user-home-backgrounds/23/new.webp");
        assertThat(response.homeBackgroundUrl()).isEqualTo("cosbg:user-home-backgrounds/23/new.webp");
    }

    @Test
    void getCloudUserProfileBackfillsMissingProfileWithDefaultQuotaAndLegacyBackground() {
        SysUser user = regularUser(23L, 512L);
        user.setHomeBackgroundUrl("cosbg:user-home-backgrounds/23/legacy.webp");

        when(sysUserRepository.findById(23L)).thenReturn(Optional.of(user));
        when(cloudUserProfileRepository.findById(23L)).thenReturn(Optional.empty());
        when(cloudUserProfileRepository.save(any(CloudUserProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = cloudUserProfileService.getCloudUserProfile(23L);

        assertThat(response.userId()).isEqualTo(23L);
        assertThat(response.storageQuotaBytes()).isEqualTo(2048L);
        assertThat(response.homeBackgroundUrl()).isEqualTo("cosbg:user-home-backgrounds/23/legacy.webp");
    }

    @Test
    void initializeDefaultNewUserProfilePersistsDefaultQuotaAfterIdentityCreation() {
        SysUser user = regularUser(72L, null);
        IdentityAccount account = identityAccount(72L, UserRole.USER);

        when(sysUserRepository.findById(72L)).thenReturn(Optional.of(user));
        when(cloudUserProfileRepository.findById(72L)).thenReturn(Optional.empty());
        when(storageQuotaService.getDefaultUserQuotaBytes()).thenReturn(2048L);
        when(cloudUserProfileRepository.save(any(CloudUserProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = cloudUserProfileService.initializeDefaultNewUserProfile(account);

        assertThat(user.getStorageQuotaBytes()).isNull();
        assertThat(result.storageQuotaBytes()).isEqualTo(2048L);
    }

    @Test
    void initializeAdminCreatedUserProfileOwnsQuotaAndBackgroundInitialization() {
        SysUser admin = new SysUser();
        ReflectionTestUtils.setField(admin, "id", 1L);
        admin.setPhoneNumber("13800000001");
        admin.setNickname("Admin");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setStorageQuotaBytes(2048L);
        SysUser targetUser = regularUser(23L, null);
        IdentityAccount account = identityAccount(23L, UserRole.USER);
        CloudUserProfileEntity adminProfile =
                cloudProfile(1L, "cosbg:user-home-backgrounds/1/source.webp", 2048L);

        when(sysUserRepository.findById(23L)).thenReturn(Optional.of(targetUser));
        when(cloudUserProfileRepository.findById(23L)).thenReturn(Optional.empty());
        when(storageQuotaService.normalizeQuotaBytes(4096L, "用户最大存储额度")).thenReturn(4096L);
        when(sysUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(cloudUserProfileRepository.findById(1L)).thenReturn(Optional.of(adminProfile));
        when(cosFileStorageService.duplicateUserHomeBackground(23L, "user-home-backgrounds/1/source.webp"))
                .thenReturn(new CosFileStorageService.StoredCosFile("user-home-backgrounds/23/copied.webp", "image/webp", 3L));
        when(cloudUserProfileRepository.save(any(CloudUserProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = cloudUserProfileService.initializeAdminCreatedUserProfile(1L, account, 4096L, true);

        assertThat(targetUser.getStorageQuotaBytes()).isNull();
        assertThat(targetUser.getHomeBackgroundUrl()).isNull();
        assertThat(result.storageQuotaBytes()).isEqualTo(4096L);
        assertThat(result.homeBackgroundUrl()).isEqualTo("cosbg:user-home-backgrounds/23/copied.webp");
    }

    @Test
    void initializeAdminCreatedAdminProfileUsesDefaultQuotaWhenQuotaRequestIsMissing() {
        SysUser user = regularUser(81L, null);
        IdentityAccount account = identityAccount(81L, UserRole.ADMIN);

        when(sysUserRepository.findById(81L)).thenReturn(Optional.of(user));
        when(cloudUserProfileRepository.findById(81L)).thenReturn(Optional.empty());
        when(storageQuotaService.getDefaultUserQuotaBytes()).thenReturn(2048L);
        when(cloudUserProfileRepository.save(any(CloudUserProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = cloudUserProfileService.initializeAdminCreatedUserProfile(1L, account, null, false);

        assertThat(user.getStorageQuotaBytes()).isNull();
        assertThat(result.storageQuotaBytes()).isEqualTo(2048L);
    }

    @Test
    void toUserProfileCombinesIdentityAndCloudProfileForCompatibleResponse() {
        IdentityAccount account = new IdentityAccount(
                23L,
                null,
                "user@example.com",
                "Alicia",
                "cos:user-avatars/23/avatar.webp",
                UserRole.USER,
                UserStatus.ACTIVE,
                LocalDateTime.of(2026, 4, 29, 15, 30)
        );
        CloudUserProfileService.CloudUserProfile cloudProfile =
                new CloudUserProfileService.CloudUserProfile(23L, "cosbg:user-home-backgrounds/23/bg.webp", 4096L);

        when(storageQuotaService.getUsedBytes(23L)).thenReturn(1536L);

        var response = cloudUserProfileService.toUserProfile(account, cloudProfile);

        assertThat(response.phoneNumber()).isEmpty();
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.avatarUrl()).isEqualTo("cos:user-avatars/23/avatar.webp");
        assertThat(response.homeBackgroundUrl()).isEqualTo("cosbg:user-home-backgrounds/23/bg.webp");
        assertThat(response.storageQuotaBytes()).isEqualTo(4096L);
        assertThat(response.usedBytes()).isEqualTo(1536L);
        assertThat(response.remainingBytes()).isEqualTo(2560L);
    }

    private IdentityAccount identityAccount(Long id, UserRole role) {
        return new IdentityAccount(
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

    private CloudUserProfileEntity cloudProfile(Long userId, String homeBackgroundUrl, Long storageQuotaBytes) {
        CloudUserProfileEntity profile = new CloudUserProfileEntity();
        profile.setIdentityUserId(userId);
        profile.setHomeBackgroundUrl(homeBackgroundUrl);
        profile.setStorageQuotaBytes(storageQuotaBytes);
        return profile;
    }
}
