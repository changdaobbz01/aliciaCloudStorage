package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminUpdateUserQuotaRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock
    private IdentityAccountService identityAccountService;

    @Mock
    private CloudUserProfileService cloudUserProfileService;

    @InjectMocks
    private UserAccountService userAccountService;

    @Test
    void loginCombinesIdentitySessionWithCloudProfileResponse() {
        LoginRequest request = new LoginRequest("Email-User@Example.COM", null, null, "Passw0rd");
        IdentityAccount account = regularIdentityAccount(18L);
        UserProfileResponse profile = profile(account, 4096L, 1024L, 3072L);

        when(identityAccountService.login(request)).thenReturn(new IdentityLoginSession("token", account));
        when(cloudUserProfileService.toUserProfile(account)).thenReturn(profile);

        var response = userAccountService.login(request);

        assertThat(response.token()).isEqualTo("token");
        assertThat(response.user()).isSameAs(profile);
    }

    @Test
    void createUserResolvesCloudQuotaBeforePersistingIdentity() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "13800000001",
                "Quota Admin",
                null,
                true,
                "Admin@123",
                "ADMIN",
                null
        );
        IdentityAccount account = regularIdentityAccount(55L, UserRole.ADMIN);
        CloudUserProfileService.CloudUserProfile cloudProfile =
                new CloudUserProfileService.CloudUserProfile(55L, "cosbg:user-home-backgrounds/55/bg.webp", 2048L);
        UserProfileResponse responseProfile = profile(account, null, 1024L, null);

        when(identityAccountService.normalizeRole("ADMIN")).thenReturn(UserRole.ADMIN);
        when(cloudUserProfileService.resolveInitialStorageQuota(UserRole.ADMIN, null)).thenReturn(2048L);
        when(identityAccountService.createUser(request, 2048L)).thenReturn(account);
        when(cloudUserProfileService.inheritAdminHomeBackground(1L, true, 55L)).thenReturn(cloudProfile);
        when(cloudUserProfileService.toUserProfile(account, cloudProfile)).thenReturn(responseProfile);

        var response = userAccountService.createUser(1L, request);

        assertThat(response).isSameAs(responseProfile);
        verify(identityAccountService).createUser(request, 2048L);
    }

    @Test
    void uploadHomeBackgroundCombinesUpdatedCloudProfileWithCurrentIdentity() {
        MockMultipartFile file = new MockMultipartFile("file", "bg.webp", "image/webp", new byte[]{1, 2, 3});
        IdentityAccount account = regularIdentityAccount(23L);
        CloudUserProfileService.CloudUserProfile cloudProfile =
                new CloudUserProfileService.CloudUserProfile(23L, "cosbg:user-home-backgrounds/23/new.webp", 2048L);
        UserProfileResponse responseProfile = profile(account, 2048L, 512L, 1536L);

        when(cloudUserProfileService.uploadCurrentUserHomeBackground(23L, file)).thenReturn(cloudProfile);
        when(identityAccountService.getCurrentUser(23L)).thenReturn(account);
        when(cloudUserProfileService.toUserProfile(account, cloudProfile)).thenReturn(responseProfile);

        var response = userAccountService.uploadCurrentUserHomeBackground(23L, file);

        assertThat(response).isSameAs(responseProfile);
    }

    @Test
    void updateUserStorageQuotaUsesAdminIdentityLookupForCompatibleResponse() {
        AdminUpdateUserQuotaRequest request = new AdminUpdateUserQuotaRequest(4096L);
        IdentityAccount account = regularIdentityAccount(77L);
        CloudUserProfileService.CloudUserProfile cloudProfile =
                new CloudUserProfileService.CloudUserProfile(77L, null, 4096L);
        UserProfileResponse responseProfile = profile(account, 4096L, 1536L, 2560L);

        when(cloudUserProfileService.updateUserStorageQuota(77L, request)).thenReturn(cloudProfile);
        when(identityAccountService.getUser(77L)).thenReturn(account);
        when(cloudUserProfileService.toUserProfile(account, cloudProfile)).thenReturn(responseProfile);

        var response = userAccountService.updateUserStorageQuota(77L, request);

        assertThat(response).isSameAs(responseProfile);
        verify(identityAccountService).getUser(77L);
    }

    @Test
    void createVerifiedEmailUserUsesDefaultCloudQuotaForCurrentSchema() {
        IdentityAccount account = regularIdentityAccount(72L);
        UserProfileResponse profile = profile(account, 2048L, 0L, 2048L);

        when(cloudUserProfileService.resolveDefaultStorageQuota()).thenReturn(2048L);
        when(identityAccountService.createVerifiedEmailUser("New@Example.COM", "New User", "Passw0rd", 2048L))
                .thenReturn(new IdentityLoginSession("new-token", account));
        when(cloudUserProfileService.toUserProfile(account)).thenReturn(profile);

        var response = userAccountService.createVerifiedEmailUser("New@Example.COM", "New User", "Passw0rd");

        assertThat(response.token()).isEqualTo("new-token");
        assertThat(response.user()).isSameAs(profile);
    }

    private IdentityAccount regularIdentityAccount(Long id) {
        return regularIdentityAccount(id, UserRole.USER);
    }

    private IdentityAccount regularIdentityAccount(Long id, UserRole role) {
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
