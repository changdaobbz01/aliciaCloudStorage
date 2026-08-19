package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.LoginResponse;
import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
@Transactional
public class UserAccountService {

    private final IdentityAccountService identityAccountService;
    private final IdentityAuthGateway identityAuthGateway;
    private final CloudUserProfileService cloudUserProfileService;

    public UserAccountService(
            IdentityAccountService identityAccountService,
            IdentityAuthGateway identityAuthGateway,
            CloudUserProfileService cloudUserProfileService
    ) {
        this.identityAccountService = identityAccountService;
        this.identityAuthGateway = identityAuthGateway;
        this.cloudUserProfileService = cloudUserProfileService;
    }

    public LoginResponse login(LoginRequest request) {
        IdentityLoginSession session = identityAuthGateway.login(request);
        return new LoginResponse(session.token(), cloudUserProfileService.toUserProfile(session.account()));
    }

    public UserProfileResponse getCurrentUser(String authorization) {
        return cloudUserProfileService.getCurrentUser(identityAuthGateway.me(authorization));
    }

    public UserProfileResponse updateCurrentUser(Long userId, UpdateProfileRequest request) {
        IdentityAccount account = identityAccountService.updateCurrentUser(userId, request);
        return cloudUserProfileService.toUserProfile(account);
    }

    public UserProfileResponse uploadCurrentUserAvatar(Long userId, MultipartFile file) {
        IdentityAccount account = identityAccountService.uploadCurrentUserAvatar(userId, file);
        return cloudUserProfileService.toUserProfile(account);
    }

    @Transactional(readOnly = true)
    public AvatarDownloadPayload openUserAvatar(Long userId) {
        IdentityAccountService.AvatarDownloadPayload downloadedCosFile =
                identityAccountService.openUserAvatar(userId);
        return new AvatarDownloadPayload(
                downloadedCosFile.contentType(),
                downloadedCosFile.contentLength(),
                downloadedCosFile.inputStream()
        );
    }

    @Transactional(readOnly = true)
    public CosFileStorageService.PresignedCosUrl resolveUserAvatarAccessUrl(Long userId) {
        return identityAccountService.resolveUserAvatarAccessUrl(userId);
    }

    public void changePassword(String authorization, ChangePasswordRequest request) {
        identityAuthGateway.changePassword(authorization, request);
    }

    public LoginResponse createVerifiedEmailUser(String email, String nickname, String password) {
        IdentityLoginSession session =
                identityAccountService.createVerifiedEmailUser(email, nickname, password);
        CloudUserProfileService.CloudUserProfile cloudProfile =
                cloudUserProfileService.initializeDefaultNewUserProfile(session.account());
        return new LoginResponse(session.token(), cloudUserProfileService.toUserProfile(session.account(), cloudProfile));
    }

    public String normalizeEmail(String value) {
        return identityAccountService.normalizeEmail(value);
    }

    public record AvatarDownloadPayload(
            String contentType,
            long contentLength,
            InputStream inputStream
    ) {
    }
}
