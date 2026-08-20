package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityAdminGateway;
import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminIdentityDirectoryCompatibilityServiceTest {

    @Mock
    private IdentityAdminGateway identityAdminGateway;

    @Mock
    private CloudUserProfileService cloudUserProfileService;

    @InjectMocks
    private AdminIdentityDirectoryCompatibilityService adminIdentityDirectoryCompatibilityService;

    @Test
    void listUsersCombinesIdentityUserSnapshotsWithCloudProfilesForCompatibleResponses() {
        IdentityUserSnapshot firstAccount = identityUserSnapshot(7L, UserRole.ADMIN);
        IdentityUserSnapshot secondAccount = identityUserSnapshot(8L, UserRole.USER);
        UserProfileResponse firstProfile = profile(firstAccount, null, 1024L, null);
        UserProfileResponse secondProfile = profile(secondAccount, 4096L, 1536L, 2560L);

        when(identityAdminGateway.listUsers("Bearer admin-token")).thenReturn(List.of(firstAccount, secondAccount));
        when(cloudUserProfileService.toUserProfile(firstAccount)).thenReturn(firstProfile);
        when(cloudUserProfileService.toUserProfile(secondAccount)).thenReturn(secondProfile);

        List<UserProfileResponse> responses = adminIdentityDirectoryCompatibilityService.listUsers("Bearer admin-token");

        assertThat(responses).containsExactly(firstProfile, secondProfile);
    }

    private IdentityUserSnapshot identityUserSnapshot(Long id, UserRole role) {
        return new IdentityUserSnapshot(
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

    private UserProfileResponse profile(IdentityUserSnapshot account, Long storageQuotaBytes, long usedBytes, Long remainingBytes) {
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
