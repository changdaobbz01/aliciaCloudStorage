package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.identity.UserStatus;
import com.alicia.cloudstorage.api.identity.IdentityAdminGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminIdentityCompatibilityServiceTest {

    @Mock
    private IdentityAdminGateway identityAdminGateway;

    @Mock
    private CloudUserProfileService cloudUserProfileService;

    @InjectMocks
    private AdminIdentityCompatibilityService adminIdentityCompatibilityService;

    @Test
    void listUsersCombinesIdentityUserSnapshotsWithCloudProfilesForCompatibleResponses() {
        IdentityUserSnapshot firstAccount = identityUserSnapshot(7L, UserRole.ADMIN);
        IdentityUserSnapshot secondAccount = identityUserSnapshot(8L, UserRole.USER);
        UserProfileResponse firstProfile = profile(firstAccount, null, 1024L, null);
        UserProfileResponse secondProfile = profile(secondAccount, 4096L, 1536L, 2560L);

        when(identityAdminGateway.listUsers("Bearer admin-token")).thenReturn(List.of(firstAccount, secondAccount));
        when(cloudUserProfileService.toUserProfile(firstAccount)).thenReturn(firstProfile);
        when(cloudUserProfileService.toUserProfile(secondAccount)).thenReturn(secondProfile);

        List<UserProfileResponse> responses = adminIdentityCompatibilityService.listUsers("Bearer admin-token");

        assertThat(responses).containsExactly(firstProfile, secondProfile);
    }

    @Test
    void createUserPersistsIdentityThenInitializesCloudProfile() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "13800000001",
                "Quota Admin",
                null,
                true,
                "Admin@123",
                "ADMIN",
                null
        );
        IdentityUserSnapshot account = identityUserSnapshot(55L, UserRole.ADMIN);
        CloudUserProfileService.CloudUserProfile cloudProfile =
                new CloudUserProfileService.CloudUserProfile(55L, "cosbg:user-home-backgrounds/55/bg.webp", 2048L);
        UserProfileResponse responseProfile = profile(account, null, 1024L, null);

        when(identityAdminGateway.createUser("Bearer admin-token", request)).thenReturn(account);
        when(cloudUserProfileService.initializeAdminCreatedUserProfile(1L, account, null, true)).thenReturn(cloudProfile);
        when(cloudUserProfileService.toUserProfile(account, cloudProfile)).thenReturn(responseProfile);

        UserProfileResponse response = adminIdentityCompatibilityService.createUser("Bearer admin-token", 1L, request);

        assertThat(response).isSameAs(responseProfile);
        verify(identityAdminGateway).createUser("Bearer admin-token", request);
        verify(cloudUserProfileService).initializeAdminCreatedUserProfile(1L, account, null, true);
    }

    @Test
    void resetUserPasswordDelegatesToIdentityService() {
        AdminResetUserPasswordRequest request = new AdminResetUserPasswordRequest("ResetPass@456");

        adminIdentityCompatibilityService.resetUserPassword("Bearer admin-token", 64L, request);

        verify(identityAdminGateway).resetUserPassword("Bearer admin-token", 64L, request);
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
