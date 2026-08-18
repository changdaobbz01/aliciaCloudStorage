package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.CloudUserProfileEntity;
import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.repository.CloudUserProfileRepository;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserStorageQuotaAccountReaderTest {

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private CloudUserProfileRepository cloudUserProfileRepository;

    @InjectMocks
    private SysUserStorageQuotaAccountReader storageQuotaAccountReader;

    @Test
    void requireAccountMapsOnlyQuotaRelevantUserFields() {
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setStorageQuotaBytes(1024L);
        CloudUserProfileEntity profile = new CloudUserProfileEntity();
        profile.setIdentityUserId(7L);
        profile.setStorageQuotaBytes(4096L);

        when(sysUserRepository.findById(7L)).thenReturn(Optional.of(user));
        when(cloudUserProfileRepository.findById(7L)).thenReturn(Optional.of(profile));

        StorageQuotaAccount account = storageQuotaAccountReader.requireAccount(7L);

        assertThat(account.userId()).isEqualTo(7L);
        assertThat(account.role()).isEqualTo(UserRole.USER);
        assertThat(account.storageQuotaBytes()).isEqualTo(4096L);
    }

    @Test
    void requireAccountFallsBackToLegacyQuotaWhenCloudProfileIsMissing() {
        SysUser user = new SysUser();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setStorageQuotaBytes(1024L);

        when(sysUserRepository.findById(7L)).thenReturn(Optional.of(user));
        when(cloudUserProfileRepository.findById(7L)).thenReturn(Optional.empty());

        StorageQuotaAccount account = storageQuotaAccountReader.requireAccount(7L);

        assertThat(account.storageQuotaBytes()).isEqualTo(1024L);
    }

    @Test
    void getTotalAllocatedQuotaBytesNormalizesNullRepositoryResult() {
        when(cloudUserProfileRepository.sumStorageQuotaBytes()).thenReturn(null);

        assertThat(storageQuotaAccountReader.getTotalAllocatedQuotaBytes()).isZero();
    }
}
