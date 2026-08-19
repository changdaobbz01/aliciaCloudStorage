package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AdminUpdateUserQuotaRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.identity.IdentityUserGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCloudUserProfileManagementServiceTest {

    @Mock
    private IdentityUserGateway identityUserGateway;

    @Mock
    private CloudUserProfileService cloudUserProfileService;

    @InjectMocks
    private AdminCloudUserProfileManagementService adminCloudUserProfileManagementService;

    @Test
    void updateUserStorageQuotaCombinesCloudMutationWithIdentitySnapshot() {
        AdminUpdateUserQuotaRequest request = new AdminUpdateUserQuotaRequest(4096L);
        IdentityAccount account = new IdentityAccount(
                77L,
                "13900000000",
                "user@example.com",
                "Alicia",
                null,
                UserRole.USER,
                UserStatus.ACTIVE,
                LocalDateTime.of(2026, 4, 29, 15, 30)
        );
        CloudUserProfileService.CloudUserProfile cloudProfile =
                new CloudUserProfileService.CloudUserProfile(77L, null, 4096L);
        UserProfileResponse responseProfile = new UserProfileResponse(
                77L,
                "13900000000",
                "user@example.com",
                "Alicia",
                null,
                null,
                "USER",
                "ACTIVE",
                LocalDateTime.of(2026, 4, 29, 15, 30),
                4096L,
                1536L,
                2560L
        );

        when(cloudUserProfileService.updateUserStorageQuota(77L, request)).thenReturn(cloudProfile);
        when(identityUserGateway.getUser(77L)).thenReturn(account);
        when(cloudUserProfileService.toUserProfile(account, cloudProfile)).thenReturn(responseProfile);

        UserProfileResponse response = adminCloudUserProfileManagementService.updateUserStorageQuota(77L, request);

        assertThat(response).isSameAs(responseProfile);
        verify(identityUserGateway).getUser(77L);
    }
}
