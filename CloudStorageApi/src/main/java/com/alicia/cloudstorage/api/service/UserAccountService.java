package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.auth.AuthException;
import com.alicia.cloudstorage.api.auth.TokenService;
import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminUpdateUserQuotaRequest;
import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.LoginResponse;
import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

@Service
@Transactional
public class UserAccountService {

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final CosFileStorageService cosFileStorageService;
    private final StorageQuotaService storageQuotaService;

    public UserAccountService(
            SysUserRepository sysUserRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            CosFileStorageService cosFileStorageService,
            StorageQuotaService storageQuotaService
    ) {
        this.sysUserRepository = sysUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.cosFileStorageService = cosFileStorageService;
        this.storageQuotaService = storageQuotaService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserRepository.findByPhoneNumber(normalizePhoneNumber(request.phoneNumber()))
                .orElseThrow(() -> new IllegalArgumentException("手机号或密码不正确。"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("手机号或密码不正确。");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthException("当前账号已停用。");
        }

        return new LoginResponse(tokenService.createToken(user), toUserProfile(user));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(Long userId) {
        return toUserProfile(requireActiveUser(userId));
    }

    public UserProfileResponse updateCurrentUser(Long userId, UpdateProfileRequest request) {
        SysUser user = requireActiveUser(userId);
        String phoneNumber = normalizePhoneNumber(request.phoneNumber());
        String nickname = normalizeNickname(request.nickname());
        String avatarUrl = normalizeAvatarUrl(request.avatarUrl());

        if (sysUserRepository.existsByPhoneNumberAndIdNot(phoneNumber, userId)) {
            throw new IllegalArgumentException("手机号已被其他账户使用。");
        }

        user.setPhoneNumber(phoneNumber);
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);

        return toUserProfile(sysUserRepository.save(user));
    }

    public UserProfileResponse uploadCurrentUserAvatar(Long userId, MultipartFile file) {
        SysUser user = requireActiveUser(userId);
        String oldAvatarUrl = user.getAvatarUrl();
        CosFileStorageService.StoredCosFile avatarFile = cosFileStorageService.uploadUserAvatar(userId, file);

        user.setAvatarUrl(toLocalAvatarReference(avatarFile.objectKey()));
        UserProfileResponse response = toUserProfile(sysUserRepository.save(user));
        deleteLocalAvatarQuietly(oldAvatarUrl);

        return response;
    }

    public UserProfileResponse uploadCurrentUserHomeBackground(Long userId, MultipartFile file) {
        SysUser user = requireActiveUser(userId);
        String oldHomeBackgroundUrl = user.getHomeBackgroundUrl();
        CosFileStorageService.StoredCosFile backgroundFile = cosFileStorageService.uploadUserHomeBackground(userId, file);

        user.setHomeBackgroundUrl(toLocalHomeBackgroundReference(backgroundFile.objectKey()));
        UserProfileResponse response = toUserProfile(sysUserRepository.save(user));
        deleteLocalHomeBackgroundQuietly(oldHomeBackgroundUrl);

        return response;
    }

    @Transactional(readOnly = true)
    public AvatarDownloadPayload openUserAvatar(Long userId) {
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
        String objectKey = extractLocalAvatarObjectKey(user.getAvatarUrl());

        if (objectKey == null) {
            throw new IllegalArgumentException("头像不存在。");
        }

        CosFileStorageService.DownloadedCosFile downloadedCosFile = cosFileStorageService.openFileStream(objectKey);
        return new AvatarDownloadPayload(
                downloadedCosFile.contentType(),
                downloadedCosFile.contentLength(),
                downloadedCosFile.inputStream()
        );
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

    public UserProfileResponse clearCurrentUserHomeBackground(Long userId) {
        SysUser user = requireActiveUser(userId);
        String oldHomeBackgroundUrl = user.getHomeBackgroundUrl();

        user.setHomeBackgroundUrl(null);
        UserProfileResponse response = toUserProfile(sysUserRepository.save(user));
        deleteLocalHomeBackgroundQuietly(oldHomeBackgroundUrl);

        return response;
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        SysUser user = requireActiveUser(userId);
        String oldPassword = normalizePassword(request.oldPassword(), "旧密码不能为空。");
        String newPassword = normalizePassword(request.newPassword(), "新密码不能为空。");

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("旧密码不正确。");
        }

        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度至少为 6 位。");
        }

        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("新密码不能与旧密码相同。");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        sysUserRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> listUsers() {
        return sysUserRepository.findAllByOrderByIdAsc().stream()
                .map(this::toUserProfile)
                .toList();
    }

    public UserProfileResponse createUser(AdminCreateUserRequest request) {
        String phoneNumber = normalizePhoneNumber(request.phoneNumber());
        String nickname = normalizeNickname(request.nickname());
        String password = normalizePassword(request.password(), "密码不能为空。");
        String avatarUrl = normalizeAvatarUrl(request.avatarUrl());
        UserRole role = normalizeRole(request.role());
        long storageQuotaBytes = role == UserRole.ADMIN
                ? storageQuotaService.getDefaultUserQuotaBytes()
                : storageQuotaService.normalizeQuotaBytes(request.storageQuotaBytes(), "用户最大存储额度");

        if (password.length() < 6) {
            throw new IllegalArgumentException("密码长度至少为 6 位。");
        }

        if (sysUserRepository.existsByPhoneNumber(phoneNumber)) {
            throw new IllegalArgumentException("手机号已被其他账户使用。");
        }

        SysUser user = new SysUser();
        user.setPhoneNumber(phoneNumber);
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setStorageQuotaBytes(storageQuotaBytes);

        return toUserProfile(sysUserRepository.save(user));
    }

    public UserProfileResponse updateUserStorageQuota(Long userId, AdminUpdateUserQuotaRequest request) {
        SysUser user = requireUser(userId);

        if (user.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("管理员账号不限制存储额度，无需修改。");
        }

        long storageQuotaBytes = storageQuotaService.normalizeQuotaBytes(request.storageQuotaBytes(), "用户最大存储额度");
        storageQuotaService.validateQuotaAssignment(userId, storageQuotaBytes);

        user.setStorageQuotaBytes(storageQuotaBytes);
        return toUserProfile(sysUserRepository.save(user));
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

    private UserProfileResponse toUserProfile(SysUser user) {
        long usedBytes = user.getId() == null ? 0L : storageQuotaService.getUsedBytes(user.getId());
        long quotaBytes = user.getStorageQuotaBytes() == null
                ? storageQuotaService.getDefaultUserQuotaBytes()
                : user.getStorageQuotaBytes();
        boolean admin = user.getRole() == UserRole.ADMIN;
        Long storageQuotaBytes = admin ? null : quotaBytes;
        Long remainingBytes = admin ? null : Math.max(0L, quotaBytes - usedBytes);

        return new UserProfileResponse(
                user.getId(),
                user.getPhoneNumber(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getHomeBackgroundUrl(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getCreatedAt(),
                storageQuotaBytes,
                usedBytes,
                remainingBytes
        );
    }

    private String normalizePhoneNumber(String value) {
        if (value == null) {
            throw new IllegalArgumentException("手机号不能为空。");
        }

        String phoneNumber = value.trim();
        if (!phoneNumber.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("请输入 11 位手机号。");
        }

        return phoneNumber;
    }

    private String normalizeNickname(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("昵称不能为空。");
        }

        return value.trim();
    }

    private String normalizeAvatarUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }

    private String toLocalAvatarReference(String objectKey) {
        return "cos:" + objectKey;
    }

    private String toLocalHomeBackgroundReference(String objectKey) {
        return "cosbg:" + objectKey;
    }

    private String extractLocalAvatarObjectKey(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith("cos:")) {
            return null;
        }

        String objectKey = avatarUrl.substring("cos:".length()).trim();
        return objectKey.isBlank() ? null : objectKey;
    }

    private String extractLocalHomeBackgroundObjectKey(String homeBackgroundUrl) {
        if (homeBackgroundUrl == null || !homeBackgroundUrl.startsWith("cosbg:")) {
            return null;
        }

        String objectKey = homeBackgroundUrl.substring("cosbg:".length()).trim();
        return objectKey.isBlank() ? null : objectKey;
    }

    private void deleteLocalAvatarQuietly(String avatarUrl) {
        String objectKey = extractLocalAvatarObjectKey(avatarUrl);

        if (objectKey != null) {
            cosFileStorageService.deleteObjectQuietly(objectKey);
        }
    }

    private void deleteLocalHomeBackgroundQuietly(String homeBackgroundUrl) {
        String objectKey = extractLocalHomeBackgroundObjectKey(homeBackgroundUrl);

        if (objectKey != null) {
            cosFileStorageService.deleteObjectQuietly(objectKey);
        }
    }

    private String normalizePassword(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }

        return value;
    }

    private UserRole normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return UserRole.USER;
        }

        try {
            return UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("角色只能是 ADMIN 或 USER。");
        }
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
