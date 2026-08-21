package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import com.alicia.cloudstorage.api.identity.IdentityUserGateway;
import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.identity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudUserAvatarServiceTest {

    @Mock
    private IdentityAuthGateway identityAuthGateway;

    @Mock
    private IdentityUserGateway identityUserGateway;

    @Mock
    private CloudUserProfileService cloudUserProfileService;

    @Mock
    private CosFileStorageService cosFileStorageService;

    @InjectMocks
    private CloudUserAvatarService cloudUserAvatarService;

    @Test
    void uploadCurrentUserAvatarUpdatesIdentityProfileAndDeletesOldLocalAvatar() {
        IdentityUserSnapshot currentAccount = identityUserSnapshot(
                18L,
                "13900000000",
                "user@example.com",
                "Alicia",
                "cos:user-avatars/18/old.webp"
        );
        IdentityUserSnapshot updatedAccount = identityUserSnapshot(
                18L,
                "13900000000",
                "user@example.com",
                "Alicia",
                "cos:user-avatars/18/new.webp"
        );
        UserProfileResponse profile = profile(updatedAccount, 4096L, 1024L, 3072L);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.webp", "image/webp", new byte[]{1, 2, 3});

        when(identityAuthGateway.me("Bearer token")).thenReturn(currentAccount);
        when(cosFileStorageService.uploadUserAvatar(18L, file))
                .thenReturn(new CosFileStorageService.StoredCosFile("user-avatars/18/new.webp", "image/webp", 3L));
        when(identityAuthGateway.updateProfile(eq("Bearer token"), any(UpdateProfileRequest.class)))
                .thenReturn(updatedAccount);
        when(cloudUserProfileService.toUserProfile(updatedAccount)).thenReturn(profile);

        var response = cloudUserAvatarService.uploadCurrentUserAvatar("Bearer token", file);

        ArgumentCaptor<UpdateProfileRequest> requestCaptor = ArgumentCaptor.forClass(UpdateProfileRequest.class);
        verify(identityAuthGateway).updateProfile(eq("Bearer token"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().phoneNumber()).isEqualTo("13900000000");
        assertThat(requestCaptor.getValue().nickname()).isEqualTo("Alicia");
        assertThat(requestCaptor.getValue().avatarUrl()).isEqualTo("cos:user-avatars/18/new.webp");
        verify(cosFileStorageService).deleteObjectQuietly("user-avatars/18/old.webp");
        assertThat(response).isSameAs(profile);
    }

    @Test
    void uploadCurrentUserAvatarDeletesNewAvatarWhenIdentityUpdateFails() {
        IdentityUserSnapshot currentAccount = identityUserSnapshot(
                18L,
                "13900000000",
                "user@example.com",
                "Alicia",
                "cos:user-avatars/18/old.webp"
        );
        MockMultipartFile file = new MockMultipartFile("file", "avatar.webp", "image/webp", new byte[]{1, 2, 3});

        when(identityAuthGateway.me("Bearer token")).thenReturn(currentAccount);
        when(cosFileStorageService.uploadUserAvatar(18L, file))
                .thenReturn(new CosFileStorageService.StoredCosFile("user-avatars/18/new.webp", "image/webp", 3L));
        when(identityAuthGateway.updateProfile(eq("Bearer token"), any(UpdateProfileRequest.class)))
                .thenThrow(new IllegalArgumentException("昵称不能为空。"));

        assertThatThrownBy(() -> cloudUserAvatarService.uploadCurrentUserAvatar("Bearer token", file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("昵称不能为空。");

        verify(cosFileStorageService).deleteObjectQuietly("user-avatars/18/new.webp");
        verify(cosFileStorageService, never()).deleteObjectQuietly("user-avatars/18/old.webp");
    }

    @Test
    void resolveUserAvatarAccessUrlUsesIdentityApiAvatarReference() {
        IdentityUserSnapshot account = identityUserSnapshot(
                18L,
                "13900000000",
                "user@example.com",
                "Alicia",
                "cos:user-avatars/18/avatar.webp"
        );
        CosFileStorageService.PresignedCosUrl signedUrl =
                new CosFileStorageService.PresignedCosUrl("https://files.example/avatar.webp", 600L);

        when(identityUserGateway.getUser(18L)).thenReturn(account);
        when(cosFileStorageService.createInlineDownloadUrl("user-avatars/18/avatar.webp", null, null))
                .thenReturn(signedUrl);

        var response = cloudUserAvatarService.resolveUserAvatarAccessUrl(18L);

        assertThat(response).isSameAs(signedUrl);
        verify(identityUserGateway).getUser(18L);
    }

    @Test
    void resolveUserAvatarAccessUrlRejectsMissingLocalAvatarReference() {
        IdentityUserSnapshot account = identityUserSnapshot(
                18L,
                "13900000000",
                "user@example.com",
                "Alicia",
                null
        );

        when(identityUserGateway.getUser(18L)).thenReturn(account);

        assertThatThrownBy(() -> cloudUserAvatarService.resolveUserAvatarAccessUrl(18L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Avatar not found.");

        verify(cosFileStorageService, never()).createInlineDownloadUrl(any(), any(), any());
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
