package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.CloudUserProfileEntity;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.identity.IdentityUserGateway;
import com.alicia.cloudstorage.api.repository.CloudUserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityStorageQuotaAccountReaderTest {

    @Mock
    private IdentityUserGateway identityUserGateway;

    @Mock
    private CloudUserProfileRepository cloudUserProfileRepository;

    @InjectMocks
    private IdentityStorageQuotaAccountReader storageQuotaAccountReader;

    @Test
    void requireAccountCombinesIdentityRoleWithCloudQuota() {
        CloudUserProfileEntity profile = new CloudUserProfileEntity();
        profile.setIdentityUserId(7L);
        profile.setStorageQuotaBytes(4096L);

        when(identityUserGateway.getUser(7L)).thenReturn(account(7L, UserRole.USER));
        when(cloudUserProfileRepository.findById(7L)).thenReturn(Optional.of(profile));

        StorageQuotaAccount account = storageQuotaAccountReader.requireAccount(7L);

        assertThat(account.userId()).isEqualTo(7L);
        assertThat(account.role()).isEqualTo(UserRole.USER);
        assertThat(account.storageQuotaBytes()).isEqualTo(4096L);
    }

    @Test
    void requireAccountLeavesQuotaEmptyWhenCloudProfileIsMissing() {
        when(identityUserGateway.getUser(7L)).thenReturn(account(7L, UserRole.USER));
        when(cloudUserProfileRepository.findById(7L)).thenReturn(Optional.empty());

        StorageQuotaAccount account = storageQuotaAccountReader.requireAccount(7L);

        assertThat(account.storageQuotaBytes()).isNull();
    }

    @Test
    void getTotalAllocatedQuotaBytesNormalizesNullRepositoryResult() {
        when(cloudUserProfileRepository.sumStorageQuotaBytes()).thenReturn(null);

        assertThat(storageQuotaAccountReader.getTotalAllocatedQuotaBytes()).isZero();
    }

    private IdentityAccount account(Long id, UserRole role) {
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
}
