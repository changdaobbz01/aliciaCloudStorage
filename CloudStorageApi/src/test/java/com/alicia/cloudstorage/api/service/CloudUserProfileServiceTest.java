package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.identity.IdentityAccount;
import com.alicia.cloudstorage.api.dto.AdminUpdateUserQuotaRequest;
import com.alicia.cloudstorage.api.entity.CloudUserProfileEntity;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.identity.IdentityUserGateway;
import com.alicia.cloudstorage.api.repository.CloudUserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

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
    private IdentityUserGateway identityUserGateway;

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
                identityUserGateway,
                cloudUserProfileRepository,
                cosFileStorageService,
                storageQuotaService,
                provisioningService
        );
    }

    @Test
    void updateUserQuotaPersistsNewQuota() {
        IdentityAccount user = identityAccount(77L, UserRole.USER);
        CloudUserProfileEntity profile = cloudProfile(77L, null, 1024L);

        when(identityUserGateway.getUser(77L)).thenReturn(user);
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
        when(identityUserGateway.getUser(91L)).thenReturn(identityAccount(91L, UserRole.ADMIN));

        assertThatThrownBy(() -> cloudUserProfileService.updateUserStorageQuota(91L, new AdminUpdateUserQuotaRequest(4096L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("管理员账号不限制存储额度，无需修改。");
    }

    @Test
    void uploadCurrentUserHomeBackgroundPersistsCosReference() {
        IdentityAccount user = identityAccount(23L, UserRole.USER);
        CloudUserProfileEntity profile =
                cloudProfile(23L, "cosbg:user-home-backgrounds/23/old.webp", 2048L);

        MockMultipartFile file = new MockMultipartFile("file", "bg.webp", "image/webp", new byte[]{1, 2, 3});

        when(identityUserGateway.getUser(23L)).thenReturn(user);
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
    void getCloudUserProfileBackfillsMissingProfileWithDefaultQuota() {
        when(identityUserGateway.getUser(23L)).thenReturn(identityAccount(23L, UserRole.USER));
        when(cloudUserProfileRepository.findById(23L)).thenReturn(Optional.empty());
        when(cloudUserProfileRepository.save(any(CloudUserProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = cloudUserProfileService.getCloudUserProfile(23L);

        assertThat(response.userId()).isEqualTo(23L);
        assertThat(response.storageQuotaBytes()).isEqualTo(2048L);
        assertThat(response.homeBackgroundUrl()).isNull();
    }

    @Test
    void initializeDefaultNewUserProfilePersistsDefaultQuotaAfterIdentityCreation() {
        IdentityAccount account = identityAccount(72L, UserRole.USER);

        when(cloudUserProfileRepository.findById(72L)).thenReturn(Optional.empty());
        when(storageQuotaService.getDefaultUserQuotaBytes()).thenReturn(2048L);
        when(cloudUserProfileRepository.save(any(CloudUserProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = cloudUserProfileService.initializeDefaultNewUserProfile(account);

        assertThat(result.storageQuotaBytes()).isEqualTo(2048L);
        assertThat(result.homeBackgroundUrl()).isNull();
    }

    @Test
    void initializeAdminCreatedUserProfileOwnsQuotaAndBackgroundInitialization() {
        IdentityAccount account = identityAccount(23L, UserRole.USER);
        CloudUserProfileEntity adminProfile =
                cloudProfile(1L, "cosbg:user-home-backgrounds/1/source.webp", 2048L);

        when(cloudUserProfileRepository.findById(23L)).thenReturn(Optional.empty());
        when(storageQuotaService.normalizeQuotaBytes(4096L, "用户最大存储额度")).thenReturn(4096L);
        when(identityUserGateway.getUser(1L)).thenReturn(identityAccount(1L, UserRole.ADMIN));
        when(cloudUserProfileRepository.findById(1L)).thenReturn(Optional.of(adminProfile));
        when(cosFileStorageService.duplicateUserHomeBackground(23L, "user-home-backgrounds/1/source.webp"))
                .thenReturn(new CosFileStorageService.StoredCosFile("user-home-backgrounds/23/copied.webp", "image/webp", 3L));
        when(cloudUserProfileRepository.save(any(CloudUserProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = cloudUserProfileService.initializeAdminCreatedUserProfile(1L, account, 4096L, true);

        assertThat(result.storageQuotaBytes()).isEqualTo(4096L);
        assertThat(result.homeBackgroundUrl()).isEqualTo("cosbg:user-home-backgrounds/23/copied.webp");
    }

    @Test
    void initializeAdminCreatedAdminProfileUsesDefaultQuotaWhenQuotaRequestIsMissing() {
        IdentityAccount account = identityAccount(81L, UserRole.ADMIN);

        when(cloudUserProfileRepository.findById(81L)).thenReturn(Optional.empty());
        when(storageQuotaService.getDefaultUserQuotaBytes()).thenReturn(2048L);
        when(cloudUserProfileRepository.save(any(CloudUserProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = cloudUserProfileService.initializeAdminCreatedUserProfile(1L, account, null, false);

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

    private CloudUserProfileEntity cloudProfile(Long userId, String homeBackgroundUrl, Long storageQuotaBytes) {
        CloudUserProfileEntity profile = new CloudUserProfileEntity();
        profile.setIdentityUserId(userId);
        profile.setHomeBackgroundUrl(homeBackgroundUrl);
        profile.setStorageQuotaBytes(storageQuotaBytes);
        return profile;
    }
}
