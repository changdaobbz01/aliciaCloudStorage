package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import com.alicia.cloudstorage.api.entity.CloudUserProfileEntity;
import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.identity.UserStatus;
import com.alicia.cloudstorage.api.identity.IdentityUserGateway;
import com.alicia.cloudstorage.api.repository.CloudUserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudProfileStorageQuotaAccountReaderTest {

    @Mock
    private IdentityUserGateway identityUserGateway;

    @Mock
    private CloudUserProfileRepository cloudUserProfileRepository;

    @InjectMocks
    private CloudProfileStorageQuotaAccountReader storageQuotaAccountReader;

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
    void requireAccountCarriesIdentityApplicationRoles() {
        CloudUserProfileEntity profile = new CloudUserProfileEntity();
        profile.setIdentityUserId(7L);
        profile.setStorageQuotaBytes(4096L);

        when(identityUserGateway.getUser(7L))
                .thenReturn(account(7L, UserRole.USER, Map.of("cloud", "CLOUD_ADMIN")));
        when(cloudUserProfileRepository.findById(7L)).thenReturn(Optional.of(profile));

        StorageQuotaAccount account = storageQuotaAccountReader.requireAccount(7L);

        assertThat(account.appRoles()).containsEntry("cloud", "CLOUD_ADMIN");
        assertThat(account.isAdmin()).isTrue();
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

    private IdentityUserSnapshot account(Long id, UserRole role) {
        return account(id, role, Map.of());
    }

    private IdentityUserSnapshot account(Long id, UserRole role, Map<String, String> appRoles) {
        return new IdentityUserSnapshot(
                id,
                "13900000000",
                "user@example.com",
                "Alicia",
                null,
                role,
                UserStatus.ACTIVE,
                LocalDateTime.of(2026, 4, 29, 15, 30),
                appRoles
        );
    }
}
