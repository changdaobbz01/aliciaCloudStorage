package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
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
class AdminIdentityManagementServiceTest {

    @Mock
    private IdentityAccountService identityAccountService;

    @Mock
    private CloudUserProfileService cloudUserProfileService;

    @InjectMocks
    private AdminIdentityManagementService adminIdentityManagementService;

    @Test
    void listUsersCombinesIdentityAccountsWithCloudProfilesForCompatibleResponses() {
        IdentityAccount firstAccount = identityAccount(7L, UserRole.ADMIN);
        IdentityAccount secondAccount = identityAccount(8L, UserRole.USER);
        UserProfileResponse firstProfile = profile(firstAccount, null, 1024L, null);
        UserProfileResponse secondProfile = profile(secondAccount, 4096L, 1536L, 2560L);

        when(identityAccountService.listUsers()).thenReturn(List.of(firstAccount, secondAccount));
        when(cloudUserProfileService.toUserProfile(firstAccount)).thenReturn(firstProfile);
        when(cloudUserProfileService.toUserProfile(secondAccount)).thenReturn(secondProfile);

        List<UserProfileResponse> responses = adminIdentityManagementService.listUsers();

        assertThat(responses).containsExactly(firstProfile, secondProfile);
    }

    @Test
    void createUserResolvesCloudQuotaBeforePersistingIdentityAndBackground() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "13800000001",
                "Quota Admin",
                null,
                true,
                "Admin@123",
                "ADMIN",
                null
        );
        IdentityAccount account = identityAccount(55L, UserRole.ADMIN);
        CloudUserProfileService.CloudUserProfile cloudProfile =
                new CloudUserProfileService.CloudUserProfile(55L, "cosbg:user-home-backgrounds/55/bg.webp", 2048L);
        UserProfileResponse responseProfile = profile(account, null, 1024L, null);

        when(identityAccountService.normalizeRole("ADMIN")).thenReturn(UserRole.ADMIN);
        when(cloudUserProfileService.resolveInitialStorageQuota(UserRole.ADMIN, null)).thenReturn(2048L);
        when(identityAccountService.createUser(request, 2048L)).thenReturn(account);
        when(cloudUserProfileService.inheritAdminHomeBackground(1L, true, 55L)).thenReturn(cloudProfile);
        when(cloudUserProfileService.toUserProfile(account, cloudProfile)).thenReturn(responseProfile);

        UserProfileResponse response = adminIdentityManagementService.createUser(1L, request);

        assertThat(response).isSameAs(responseProfile);
        verify(identityAccountService).createUser(request, 2048L);
    }

    @Test
    void resetUserPasswordDelegatesToIdentityService() {
        AdminResetUserPasswordRequest request = new AdminResetUserPasswordRequest("ResetPass@456");

        adminIdentityManagementService.resetUserPassword(1L, 64L, request);

        verify(identityAccountService).resetUserPassword(1L, 64L, request);
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
