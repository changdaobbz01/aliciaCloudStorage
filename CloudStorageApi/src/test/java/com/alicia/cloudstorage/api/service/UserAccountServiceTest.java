package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
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
    void createVerifiedEmailUserInitializesDefaultCloudProfileAfterIdentityCreation() {
        IdentityAccount account = regularIdentityAccount(72L);
        CloudUserProfileService.CloudUserProfile cloudProfile =
                new CloudUserProfileService.CloudUserProfile(72L, null, 2048L);
        UserProfileResponse profile = profile(account, 2048L, 0L, 2048L);

        when(identityAccountService.createVerifiedEmailUser("New@Example.COM", "New User", "Passw0rd"))
                .thenReturn(new IdentityLoginSession("new-token", account));
        when(cloudUserProfileService.initializeDefaultNewUserProfile(account)).thenReturn(cloudProfile);
        when(cloudUserProfileService.toUserProfile(account, cloudProfile)).thenReturn(profile);

        var response = userAccountService.createVerifiedEmailUser("New@Example.COM", "New User", "Passw0rd");

        assertThat(response.token()).isEqualTo("new-token");
        assertThat(response.user()).isSameAs(profile);
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
