package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.CloudUserProfileEntity;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.repository.CloudUserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudUserProfileProvisioningServiceTest {

    @Mock
    private CloudUserProfileRepository cloudUserProfileRepository;

    @Test
    void ensureCloudProfileKeepsExistingProfile() {
        CloudUserProfileProvisioningService service =
                new CloudUserProfileProvisioningService(cloudUserProfileRepository, 2048L);
        CloudUserProfileEntity existingProfile = profile(7L, 512L);

        when(cloudUserProfileRepository.findById(7L)).thenReturn(Optional.of(existingProfile));

        CloudUserProfileEntity result = service.ensureCloudProfile(account(7L));

        assertThat(result).isSameAs(existingProfile);
        verify(cloudUserProfileRepository, never()).save(any());
    }

    @Test
    void ensureCloudProfileCreatesSavedDefaultProfileFromIdentityAccount() {
        CloudUserProfileProvisioningService service =
                new CloudUserProfileProvisioningService(cloudUserProfileRepository, 53687091200L);

        when(cloudUserProfileRepository.findById(7L)).thenReturn(Optional.empty());
        when(cloudUserProfileRepository.save(any(CloudUserProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CloudUserProfileEntity result = service.ensureCloudProfile(account(7L));

        assertThat(result.getIdentityUserId()).isEqualTo(7L);
        assertThat(result.getStorageQuotaBytes()).isEqualTo(53687091200L);
        assertThat(result.getHomeBackgroundUrl()).isNull();
    }

    @Test
    void findExistingOrCreateUnsavedCloudProfileDoesNotPersistDefaultProfile() {
        CloudUserProfileProvisioningService service =
                new CloudUserProfileProvisioningService(cloudUserProfileRepository, 2048L);

        when(cloudUserProfileRepository.findById(7L)).thenReturn(Optional.empty());

        CloudUserProfileEntity result = service.findExistingOrCreateUnsavedCloudProfile(account(7L));

        assertThat(result.getIdentityUserId()).isEqualTo(7L);
        assertThat(result.getStorageQuotaBytes()).isEqualTo(2048L);
        assertThat(result.getHomeBackgroundUrl()).isNull();
        verify(cloudUserProfileRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidDefaultQuotaConfiguration() {
        assertThatThrownBy(() -> new CloudUserProfileProvisioningService(cloudUserProfileRepository, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("默认用户存储额度配置必须大于 0。");
    }

    private IdentityAccount account(Long id) {
        return new IdentityAccount(
                id,
                "13900000000",
                "user@example.com",
                "Alicia",
                null,
                UserRole.USER,
                UserStatus.ACTIVE,
                LocalDateTime.of(2026, 4, 29, 15, 30)
        );
    }

    private CloudUserProfileEntity profile(Long userId, Long storageQuotaBytes) {
        CloudUserProfileEntity profile = new CloudUserProfileEntity();
        profile.setIdentityUserId(userId);
        profile.setStorageQuotaBytes(storageQuotaBytes);
        return profile;
    }
}
