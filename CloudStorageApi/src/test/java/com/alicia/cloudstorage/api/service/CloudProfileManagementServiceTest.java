package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.identity.IdentityAccount;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.identity.UserStatus;
import com.alicia.cloudstorage.api.identity.IdentityUserGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudProfileManagementServiceTest {

    @Mock
    private IdentityUserGateway identityUserGateway;

    @Mock
    private CloudUserProfileService cloudUserProfileService;

    @InjectMocks
    private CloudProfileManagementService cloudProfileManagementService;

    @Test
    void uploadHomeBackgroundCombinesUpdatedCloudProfileWithCurrentIdentity() {
        MockMultipartFile file = new MockMultipartFile("file", "bg.webp", "image/webp", new byte[]{1, 2, 3});
        IdentityAccount account = regularIdentityAccount(23L);
        CloudUserProfileService.CloudUserProfile cloudProfile =
                new CloudUserProfileService.CloudUserProfile(23L, "cosbg:user-home-backgrounds/23/new.webp", 2048L);
        UserProfileResponse responseProfile = profile(account, 2048L, 512L, 1536L);

        when(cloudUserProfileService.uploadCurrentUserHomeBackground(23L, file)).thenReturn(cloudProfile);
        when(identityUserGateway.getUser(23L)).thenReturn(account);
        when(cloudUserProfileService.toUserProfile(account, cloudProfile)).thenReturn(responseProfile);

        var response = cloudProfileManagementService.uploadCurrentUserHomeBackground(23L, file);

        assertThat(response).isSameAs(responseProfile);
    }

    @Test
    void clearHomeBackgroundCombinesUpdatedCloudProfileWithCurrentIdentity() {
        IdentityAccount account = regularIdentityAccount(23L);
        CloudUserProfileService.CloudUserProfile cloudProfile =
                new CloudUserProfileService.CloudUserProfile(23L, null, 2048L);
        UserProfileResponse responseProfile = profile(account, 2048L, 512L, 1536L);

        when(cloudUserProfileService.clearCurrentUserHomeBackground(23L)).thenReturn(cloudProfile);
        when(identityUserGateway.getUser(23L)).thenReturn(account);
        when(cloudUserProfileService.toUserProfile(account, cloudProfile)).thenReturn(responseProfile);

        var response = cloudProfileManagementService.clearCurrentUserHomeBackground(23L);

        assertThat(response).isSameAs(responseProfile);
    }

    private IdentityAccount regularIdentityAccount(Long id) {
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

    private UserProfileResponse profile(IdentityAccount account, Long storageQuotaBytes, long usedBytes, Long remainingBytes) {
        return new UserProfileResponse(
                account.id(),
                account.phoneNumberOrEmpty(),
                account.email(),
                account.nickname(),
                account.avatarUrl(),
                null,
                account.role().name(),
                account.status().name(),
                account.createdAt(),
                storageQuotaBytes,
                usedBytes,
                remainingBytes
        );
    }
}
