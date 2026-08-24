package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudCurrentUserServiceTest {

    @Mock
    private CloudUserProfileService cloudUserProfileService;

    @InjectMocks
    private CloudCurrentUserService cloudCurrentUserService;

    @Test
    void getCurrentUserCombinesIdentityApiUserWithCloudProfileResponse() {
        IdentityUserSnapshot account = regularIdentityUserSnapshot(18L);
        UserProfileResponse profile = profile(account, 4096L, 1024L, 3072L);

        when(cloudUserProfileService.getCurrentUser(account)).thenReturn(profile);

        var response = cloudCurrentUserService.getCurrentUser(account);

        assertThat(response).isSameAs(profile);
    }

    private IdentityUserSnapshot regularIdentityUserSnapshot(Long id) {
        return identityUserSnapshot(id, "13900000000", "user@example.com", "Alicia", null);
    }

    private IdentityUserSnapshot identityUserSnapshot(
            Long id,
            String phoneNumber,
            String email,
            String nickname,
            String avatarUrl
    ) {
        return new IdentityUserSnapshot(
                id,
                phoneNumber,
                email,
                nickname,
                avatarUrl,
                UserRole.USER,
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
