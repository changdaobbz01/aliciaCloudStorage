package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.LoginResponse;
import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import com.alicia.cloudstorage.api.identity.IdentityUserGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
@Transactional
public class IdentityAccountCompatibilityService {

    private final IdentityAuthGateway identityAuthGateway;
    private final IdentityUserGateway identityUserGateway;
    private final CloudUserProfileService cloudUserProfileService;
    private final CosFileStorageService cosFileStorageService;

    public IdentityAccountCompatibilityService(
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

    public LoginResponse login(LoginRequest request) {
        IdentityLoginSession session = identityAuthGateway.login(request);
        return new LoginResponse(session.token(), cloudUserProfileService.toUserProfile(session.account()));
    }

    public UserProfileResponse getCurrentUser(String authorization) {
        return cloudUserProfileService.getCurrentUser(identityAuthGateway.me(authorization));
    }

    public UserProfileResponse updateCurrentUser(String authorization, UpdateProfileRequest request) {
        IdentityAccount account = identityAuthGateway.updateProfile(authorization, request);
        return cloudUserProfileService.toUserProfile(account);
    }

    public UserProfileResponse uploadCurrentUserAvatar(String authorization, MultipartFile file) {
        IdentityAccount currentAccount = identityAuthGateway.me(authorization);
        CosFileStorageService.StoredCosFile avatarFile =
                cosFileStorageService.uploadUserAvatar(currentAccount.id(), file);
        String avatarUrl = toLocalAvatarReference(avatarFile.objectKey());

        try {
            IdentityAccount updatedAccount = identityAuthGateway.updateProfile(
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
    public AvatarDownloadPayload openUserAvatar(Long userId) {
        String objectKey = requireLocalAvatarObjectKey(identityUserGateway.getUser(userId).avatarUrl());
        CosFileStorageService.DownloadedCosFile downloadedCosFile = cosFileStorageService.openFileStream(objectKey);
        return new AvatarDownloadPayload(
                downloadedCosFile.contentType(),
                downloadedCosFile.contentLength(),
                downloadedCosFile.inputStream()
        );
    }

    @Transactional(readOnly = true)
    public CosFileStorageService.PresignedCosUrl resolveUserAvatarAccessUrl(Long userId) {
        String objectKey = requireLocalAvatarObjectKey(identityUserGateway.getUser(userId).avatarUrl());
        return cosFileStorageService.createInlineDownloadUrl(objectKey, null, null);
    }

    public void changePassword(String authorization, ChangePasswordRequest request) {
        identityAuthGateway.changePassword(authorization, request);
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

    public record AvatarDownloadPayload(
            String contentType,
            long contentLength,
            InputStream inputStream
    ) {
    }
}
