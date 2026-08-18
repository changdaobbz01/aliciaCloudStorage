package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.api.dto.AdminUpdateUserQuotaRequest;
import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.LoginResponse;
import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.entity.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

@Service
@Transactional
public class UserAccountService {

    private final IdentityAccountService identityAccountService;
    private final CloudUserProfileService cloudUserProfileService;

    public UserAccountService(
            IdentityAccountService identityAccountService,
            CloudUserProfileService cloudUserProfileService
    ) {
        this.identityAccountService = identityAccountService;
        this.cloudUserProfileService = cloudUserProfileService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        IdentityLoginSession session = identityAccountService.login(request);
        return new LoginResponse(session.token(), cloudUserProfileService.toUserProfile(session.account()));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(Long userId) {
        return cloudUserProfileService.getCurrentUser(identityAccountService.getCurrentUser(userId));
    }

    public UserProfileResponse updateCurrentUser(Long userId, UpdateProfileRequest request) {
        IdentityAccount account = identityAccountService.updateCurrentUser(userId, request);
        return cloudUserProfileService.toUserProfile(account);
    }

    public UserProfileResponse uploadCurrentUserAvatar(Long userId, MultipartFile file) {
        IdentityAccount account = identityAccountService.uploadCurrentUserAvatar(userId, file);
        return cloudUserProfileService.toUserProfile(account);
    }

    public UserProfileResponse uploadCurrentUserHomeBackground(Long userId, MultipartFile file) {
        CloudUserProfileService.CloudUserProfile cloudProfile =
                cloudUserProfileService.uploadCurrentUserHomeBackground(userId, file);
        IdentityAccount account = identityAccountService.getCurrentUser(userId);
        return cloudUserProfileService.toUserProfile(account, cloudProfile);
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
    public HomeBackgroundDownloadPayload openUserHomeBackground(Long userId) {
        CloudUserProfileService.HomeBackgroundDownloadPayload downloadedCosFile =
                cloudUserProfileService.openUserHomeBackground(userId);
        return new HomeBackgroundDownloadPayload(
                downloadedCosFile.contentType(),
                downloadedCosFile.contentLength(),
                downloadedCosFile.inputStream()
        );
    }

    @Transactional(readOnly = true)
    public CosFileStorageService.PresignedCosUrl resolveUserAvatarAccessUrl(Long userId) {
        return identityAccountService.resolveUserAvatarAccessUrl(userId);
    }

    @Transactional(readOnly = true)
    public CosFileStorageService.PresignedCosUrl resolveUserHomeBackgroundAccessUrl(Long userId) {
        return cloudUserProfileService.resolveUserHomeBackgroundAccessUrl(userId);
    }

    public UserProfileResponse clearCurrentUserHomeBackground(Long userId) {
        CloudUserProfileService.CloudUserProfile cloudProfile =
                cloudUserProfileService.clearCurrentUserHomeBackground(userId);
        IdentityAccount account = identityAccountService.getCurrentUser(userId);
        return cloudUserProfileService.toUserProfile(account, cloudProfile);
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        identityAccountService.changePassword(userId, request);
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> listUsers() {
        return identityAccountService.listUsers().stream()
                .map(cloudUserProfileService::toUserProfile)
                .toList();
    }

    public UserProfileResponse createUser(Long adminUserId, AdminCreateUserRequest request) {
        UserRole role = identityAccountService.normalizeRole(request.role());
        long storageQuotaBytes = cloudUserProfileService.resolveInitialStorageQuota(role, request.storageQuotaBytes());
        IdentityAccount account = identityAccountService.createUser(request, storageQuotaBytes);
        CloudUserProfileService.CloudUserProfile cloudProfile =
                cloudUserProfileService.inheritAdminHomeBackground(
                        adminUserId,
                        request.inheritAdminBackground(),
                        account.id()
                );

        return cloudUserProfileService.toUserProfile(account, cloudProfile);
    }

    public LoginResponse createVerifiedEmailUser(String email, String nickname, String password) {
        long storageQuotaBytes = cloudUserProfileService.resolveDefaultStorageQuota();
        IdentityLoginSession session =
                identityAccountService.createVerifiedEmailUser(email, nickname, password, storageQuotaBytes);
        return new LoginResponse(session.token(), cloudUserProfileService.toUserProfile(session.account()));
    }

    public UserProfileResponse updateUserStorageQuota(Long userId, AdminUpdateUserQuotaRequest request) {
        CloudUserProfileService.CloudUserProfile cloudProfile =
                cloudUserProfileService.updateUserStorageQuota(userId, request);
        IdentityAccount account = identityAccountService.getUser(userId);
        return cloudUserProfileService.toUserProfile(account, cloudProfile);
    }

    public void resetUserPassword(Long adminUserId, Long targetUserId, AdminResetUserPasswordRequest request) {
        identityAccountService.resetUserPassword(adminUserId, targetUserId, request);
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

    public record HomeBackgroundDownloadPayload(
            String contentType,
            long contentLength,
            InputStream inputStream
    ) {
    }
}
