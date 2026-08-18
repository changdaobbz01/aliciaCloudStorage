package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.auth.AuthException;
import com.alicia.cloudstorage.api.dto.AdminUpdateUserQuotaRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
@Transactional
public class CloudUserProfileService {

    private final SysUserRepository sysUserRepository;
    private final CosFileStorageService cosFileStorageService;
    private final StorageQuotaService storageQuotaService;

    public CloudUserProfileService(
            SysUserRepository sysUserRepository,
            CosFileStorageService cosFileStorageService,
            StorageQuotaService storageQuotaService
    ) {
        this.sysUserRepository = sysUserRepository;
        this.cosFileStorageService = cosFileStorageService;
        this.storageQuotaService = storageQuotaService;
    }

    @Transactional(readOnly = true)
    public CloudUserProfile getCloudUserProfile(Long userId) {
        return toCloudUserProfile(requireUser(userId));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(IdentityAccount account) {
        if (account.status() != UserStatus.ACTIVE) {
            throw new AuthException("当前账号已停用。");
        }

        return toUserProfile(account);
    }

    public CloudUserProfile uploadCurrentUserHomeBackground(Long userId, MultipartFile file) {
        SysUser user = requireActiveUser(userId);
        String oldHomeBackgroundUrl = user.getHomeBackgroundUrl();
        CosFileStorageService.StoredCosFile backgroundFile = cosFileStorageService.uploadUserHomeBackground(userId, file);

        user.setHomeBackgroundUrl(toLocalHomeBackgroundReference(backgroundFile.objectKey()));
        CloudUserProfile profile = toCloudUserProfile(sysUserRepository.save(user));
        deleteLocalHomeBackgroundQuietly(oldHomeBackgroundUrl);

        return profile;
    }

    @Transactional(readOnly = true)
    public HomeBackgroundDownloadPayload openUserHomeBackground(Long userId) {
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
        String objectKey = extractLocalHomeBackgroundObjectKey(user.getHomeBackgroundUrl());

        if (objectKey == null) {
            throw new IllegalArgumentException("主页背景图不存在。");
        }

        CosFileStorageService.DownloadedCosFile downloadedCosFile = cosFileStorageService.openFileStream(objectKey);
        return new HomeBackgroundDownloadPayload(
                downloadedCosFile.contentType(),
                downloadedCosFile.contentLength(),
                downloadedCosFile.inputStream()
        );
    }

    @Transactional(readOnly = true)
    public CosFileStorageService.PresignedCosUrl resolveUserHomeBackgroundAccessUrl(Long userId) {
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        String objectKey = extractLocalHomeBackgroundObjectKey(user.getHomeBackgroundUrl());

        if (objectKey == null) {
            throw new IllegalArgumentException("Home background not found.");
        }

        return cosFileStorageService.createInlineDownloadUrl(objectKey, null, null);
    }

    public CloudUserProfile clearCurrentUserHomeBackground(Long userId) {
        SysUser user = requireActiveUser(userId);
        String oldHomeBackgroundUrl = user.getHomeBackgroundUrl();

        user.setHomeBackgroundUrl(null);
        CloudUserProfile profile = toCloudUserProfile(sysUserRepository.save(user));
        deleteLocalHomeBackgroundQuietly(oldHomeBackgroundUrl);

        return profile;
    }

    public CloudUserProfile updateUserStorageQuota(Long userId, AdminUpdateUserQuotaRequest request) {
        SysUser user = requireUser(userId);

        if (user.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("管理员账号不限制存储额度，无需修改。");
        }

        long storageQuotaBytes = storageQuotaService.normalizeQuotaBytes(request.storageQuotaBytes(), "用户最大存储额度");
        storageQuotaService.validateQuotaAssignment(userId, storageQuotaBytes);

        user.setStorageQuotaBytes(storageQuotaBytes);
        return toCloudUserProfile(sysUserRepository.save(user));
    }

    public long resolveInitialStorageQuota(UserRole role, Long requestedQuotaBytes) {
        return role == UserRole.ADMIN
                ? storageQuotaService.getDefaultUserQuotaBytes()
                : storageQuotaService.normalizeQuotaBytes(requestedQuotaBytes, "用户最大存储额度");
    }

    public long resolveDefaultStorageQuota() {
        return storageQuotaService.getDefaultUserQuotaBytes();
    }

    public CloudUserProfile inheritAdminHomeBackground(Long adminUserId, boolean inheritAdminBackground, Long targetUserId) {
        SysUser targetUser = requireUser(targetUserId);
        if (!inheritAdminBackground) {
            return toCloudUserProfile(targetUser);
        }

        String inheritedHomeBackgroundUrl = resolveInheritedHomeBackgroundUrl(adminUserId, targetUserId);
        if (inheritedHomeBackgroundUrl == null) {
            return toCloudUserProfile(targetUser);
        }

        targetUser.setHomeBackgroundUrl(inheritedHomeBackgroundUrl);
        return toCloudUserProfile(sysUserRepository.save(targetUser));
    }

    public UserProfileResponse toUserProfile(IdentityAccount account) {
        return toUserProfile(account, getCloudUserProfile(account.id()));
    }

    public UserProfileResponse toUserProfile(IdentityAccount account, CloudUserProfile cloudProfile) {
        long usedBytes = account.id() == null ? 0L : storageQuotaService.getUsedBytes(account.id());
        long quotaBytes = cloudProfile.storageQuotaBytes() == null
                ? storageQuotaService.getDefaultUserQuotaBytes()
                : cloudProfile.storageQuotaBytes();
        boolean admin = account.role() == UserRole.ADMIN;
        Long storageQuotaBytes = admin ? null : quotaBytes;
        Long remainingBytes = admin ? null : Math.max(0L, quotaBytes - usedBytes);

        return new UserProfileResponse(
                account.id(),
                account.phoneNumberOrEmpty(),
                account.email(),
                account.nickname(),
                account.avatarUrl(),
                cloudProfile.homeBackgroundUrl(),
                account.role().name(),
                account.status().name(),
                account.createdAt(),
                storageQuotaBytes,
                usedBytes,
                remainingBytes
        );
    }

    private SysUser requireActiveUser(Long userId) {
        SysUser user = requireUser(userId);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthException("当前账号已停用。");
        }

        return user;
    }

    private SysUser requireUser(Long userId) {
        return sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
    }

    private CloudUserProfile toCloudUserProfile(SysUser user) {
        return new CloudUserProfile(
                user.getId(),
                user.getHomeBackgroundUrl(),
                user.getStorageQuotaBytes()
        );
    }

    private String resolveInheritedHomeBackgroundUrl(Long adminUserId, Long targetUserId) {
        SysUser adminUser = requireActiveUser(adminUserId);
        String sourceHomeBackgroundUrl = adminUser.getHomeBackgroundUrl();
        if (sourceHomeBackgroundUrl == null || sourceHomeBackgroundUrl.isBlank()) {
            return null;
        }

        String sourceObjectKey = extractLocalHomeBackgroundObjectKey(sourceHomeBackgroundUrl);
        if (sourceObjectKey == null) {
            return sourceHomeBackgroundUrl;
        }

        CosFileStorageService.StoredCosFile duplicatedBackground =
                cosFileStorageService.duplicateUserHomeBackground(targetUserId, sourceObjectKey);
        return toLocalHomeBackgroundReference(duplicatedBackground.objectKey());
    }

    private String toLocalHomeBackgroundReference(String objectKey) {
        return "cosbg:" + objectKey;
    }

    private String extractLocalHomeBackgroundObjectKey(String homeBackgroundUrl) {
        if (homeBackgroundUrl == null || !homeBackgroundUrl.startsWith("cosbg:")) {
            return null;
        }

        String objectKey = homeBackgroundUrl.substring("cosbg:".length()).trim();
        return objectKey.isBlank() ? null : objectKey;
    }

    private void deleteLocalHomeBackgroundQuietly(String homeBackgroundUrl) {
        String objectKey = extractLocalHomeBackgroundObjectKey(homeBackgroundUrl);

        if (objectKey != null) {
            cosFileStorageService.deleteObjectQuietly(objectKey);
        }
    }

    public record CloudUserProfile(
            Long userId,
            String homeBackgroundUrl,
            Long storageQuotaBytes
    ) {
    }

    public record HomeBackgroundDownloadPayload(
            String contentType,
            long contentLength,
            InputStream inputStream
    ) {
    }
}
