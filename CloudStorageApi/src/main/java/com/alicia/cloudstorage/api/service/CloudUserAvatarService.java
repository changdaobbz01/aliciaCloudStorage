package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import com.alicia.cloudstorage.api.identity.IdentityUserGateway;
import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class CloudUserAvatarService {

    private final IdentityAuthGateway identityAuthGateway;
    private final IdentityUserGateway identityUserGateway;
    private final CloudUserProfileService cloudUserProfileService;
    private final CosFileStorageService cosFileStorageService;

    public CloudUserAvatarService(
            IdentityAuthGateway identityAuthGateway,
            IdentityUserGateway identityUserGateway,
            CloudUserProfileService cloudUserProfileService,
            CosFileStorageService cosFileStorageService
    ) {
        this.identityAuthGateway = identityAuthGateway;
        this.identityUserGateway = identityUserGateway;
        this.cloudUserProfileService = cloudUserProfileService;
        this.cosFileStorageService = cosFileStorageService;
    }

    public UserProfileResponse uploadCurrentUserAvatar(
            IdentityUserSnapshot currentAccount,
            String authorization,
            MultipartFile file
    ) {
        CosFileStorageService.StoredCosFile avatarFile =
                cosFileStorageService.uploadUserAvatar(currentAccount.id(), file);
        String avatarUrl = toLocalAvatarReference(avatarFile.objectKey());

        try {
            IdentityUserSnapshot updatedAccount = identityAuthGateway.updateProfile(
                    authorization,
                    new UpdateProfileRequest(currentAccount.phoneNumber(), currentAccount.nickname(), avatarUrl)
            );
            deleteLocalAvatarQuietly(currentAccount.avatarUrl());
            return cloudUserProfileService.toUserProfile(updatedAccount);
        } catch (RuntimeException ex) {
            cosFileStorageService.deleteObjectQuietly(avatarFile.objectKey());
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public CosFileStorageService.PresignedCosUrl resolveUserAvatarAccessUrl(Long userId) {
        String objectKey = requireLocalAvatarObjectKey(identityUserGateway.getUser(userId).avatarUrl());
        return cosFileStorageService.createInlineDownloadUrl(objectKey, null, null);
    }

    private String toLocalAvatarReference(String objectKey) {
        return "cos:" + objectKey;
    }

    private String extractLocalAvatarObjectKey(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith("cos:")) {
            return null;
        }

        String objectKey = avatarUrl.substring("cos:".length()).trim();
        return objectKey.isBlank() ? null : objectKey;
    }

    private String requireLocalAvatarObjectKey(String avatarUrl) {
        String objectKey = extractLocalAvatarObjectKey(avatarUrl);
        if (objectKey == null) {
            throw new IllegalArgumentException("Avatar not found.");
        }

        return objectKey;
    }

    private void deleteLocalAvatarQuietly(String avatarUrl) {
        String objectKey = extractLocalAvatarObjectKey(avatarUrl);

        if (objectKey != null) {
            cosFileStorageService.deleteObjectQuietly(objectKey);
        }
    }
}
