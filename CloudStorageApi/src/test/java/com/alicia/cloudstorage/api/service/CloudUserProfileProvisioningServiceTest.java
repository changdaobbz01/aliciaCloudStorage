package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.CloudUserProfileEntity;
import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.repository.CloudUserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
    void ensureCloudProfileReturnsExistingProfile() {
        CloudUserProfileProvisioningService service =
                new CloudUserProfileProvisioningService(cloudUserProfileRepository, 2048L);
        SysUser user = user(7L, 512L);
        CloudUserProfileEntity existingProfile = profile(7L, 4096L);

        when(cloudUserProfileRepository.findById(7L)).thenReturn(Optional.of(existingProfile));

        assertThat(service.ensureCloudProfile(user)).isSameAs(existingProfile);
        verify(cloudUserProfileRepository, never()).save(any());
    }

    @Test
    void ensureCloudProfileCreatesDefaultProfileForIdentityUser() {
        CloudUserProfileProvisioningService service =
                new CloudUserProfileProvisioningService(cloudUserProfileRepository, 53687091200L);
        SysUser user = user(7L, 536870912L);
        user.setHomeBackgroundUrl("cosbg:user-home-backgrounds/7/legacy.webp");

        when(cloudUserProfileRepository.findById(7L)).thenReturn(Optional.empty());
        when(cloudUserProfileRepository.save(any(CloudUserProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CloudUserProfileEntity result = service.ensureCloudProfile(user);

        assertThat(result.getIdentityUserId()).isEqualTo(7L);
        assertThat(result.getStorageQuotaBytes()).isEqualTo(53687091200L);
        assertThat(result.getHomeBackgroundUrl()).isEqualTo("cosbg:user-home-backgrounds/7/legacy.webp");

        ArgumentCaptor<CloudUserProfileEntity> profileCaptor =
                ArgumentCaptor.forClass(CloudUserProfileEntity.class);
        verify(cloudUserProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getStorageQuotaBytes()).isEqualTo(53687091200L);
    }

    @Test
    void constructorRejectsInvalidDefaultQuota() {
        assertThatThrownBy(() -> new CloudUserProfileProvisioningService(cloudUserProfileRepository, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("默认用户存储额度配置必须大于 0。");
    }

    private SysUser user(Long id, Long legacyStorageQuotaBytes) {
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", id);
        user.setStorageQuotaBytes(legacyStorageQuotaBytes);
        return user;
    }

    private CloudUserProfileEntity profile(Long userId, Long storageQuotaBytes) {
        CloudUserProfileEntity profile = new CloudUserProfileEntity();
        profile.setIdentityUserId(userId);
        profile.setStorageQuotaBytes(storageQuotaBytes);
        return profile;
    }
}
