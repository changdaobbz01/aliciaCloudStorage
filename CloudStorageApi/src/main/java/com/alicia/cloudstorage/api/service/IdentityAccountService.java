package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.auth.AuthException;
import com.alicia.cloudstorage.api.auth.TokenService;
import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.repository.SysUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class IdentityAccountService {

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final CosFileStorageService cosFileStorageService;

    public IdentityAccountService(
            SysUserRepository sysUserRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            CosFileStorageService cosFileStorageService
    ) {
        this.sysUserRepository = sysUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.cosFileStorageService = cosFileStorageService;
    }

    @Transactional(readOnly = true)
    public IdentityLoginSession login(LoginRequest request) {
        LoginIdentifier loginIdentifier = normalizeLoginIdentifier(request);
        SysUser user = switch (loginIdentifier.type()) {
            case EMAIL -> sysUserRepository.findByEmail(loginIdentifier.value())
                    .orElseThrow(() -> new IllegalArgumentException("账号或密码不正确。"));
            case PHONE -> sysUserRepository.findByPhoneNumber(loginIdentifier.value())
                    .orElseThrow(() -> new IllegalArgumentException("账号或密码不正确。"));
        };

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("账号或密码不正确。");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthException("当前账号已停用。");
        }

        return new IdentityLoginSession(tokenService.createToken(user), IdentityAccount.from(user));
    }

    @Transactional(readOnly = true)
    public IdentityAccount getCurrentUser(Long userId) {
        return IdentityAccount.from(requireActiveUser(userId));
    }

    @Transactional(readOnly = true)
    public IdentityAccount getUser(Long userId) {
        return IdentityAccount.from(requireUser(userId));
    }

    public IdentityAccount updateCurrentUser(Long userId, UpdateProfileRequest request) {
        SysUser user = requireActiveUser(userId);
        String phoneNumber = normalizeOptionalPhoneNumber(request.phoneNumber());
        String nickname = normalizeNickname(request.nickname());
        String avatarUrl = normalizeAvatarUrl(request.avatarUrl());

        if (phoneNumber == null && user.getEmail() == null) {
            throw new IllegalArgumentException("手机号不能为空。");
        }

        if (phoneNumber != null && sysUserRepository.existsByPhoneNumberAndIdNot(phoneNumber, userId)) {
            throw new IllegalArgumentException("手机号已被其他账户使用。");
        }

        user.setPhoneNumber(phoneNumber);
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);

        return IdentityAccount.from(sysUserRepository.save(user));
    }

    public IdentityAccount uploadCurrentUserAvatar(Long userId, MultipartFile file) {
        SysUser user = requireActiveUser(userId);
        String oldAvatarUrl = user.getAvatarUrl();
        CosFileStorageService.StoredCosFile avatarFile = cosFileStorageService.uploadUserAvatar(userId, file);

        user.setAvatarUrl(toLocalAvatarReference(avatarFile.objectKey()));
        IdentityAccount account = IdentityAccount.from(sysUserRepository.save(user));
        deleteLocalAvatarQuietly(oldAvatarUrl);

        return account;
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
    public CosFileStorageService.PresignedCosUrl resolveUserAvatarAccessUrl(Long userId) {
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        String objectKey = extractLocalAvatarObjectKey(user.getAvatarUrl());

        if (objectKey == null) {
            throw new IllegalArgumentException("Avatar not found.");
        }

        return cosFileStorageService.createInlineDownloadUrl(objectKey, null, null);
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
        invalidateTokens(user);
        sysUserRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<IdentityAccount> listUsers() {
        return sysUserRepository.findAllByOrderByIdAsc().stream()
                .map(IdentityAccount::from)
                .toList();
    }

    public IdentityAccount createUser(AdminCreateUserRequest request, long initialStorageQuotaBytes) {
        String phoneNumber = normalizePhoneNumber(request.phoneNumber());
        String nickname = normalizeNickname(request.nickname());
        String password = normalizePassword(request.password(), "密码不能为空。");
        String avatarUrl = normalizeAvatarUrl(request.avatarUrl());
        UserRole role = normalizeRole(request.role());

        if (password.length() < 6) {
            throw new IllegalArgumentException("密码长度至少为 6 位。");
        }

        if (sysUserRepository.existsByPhoneNumber(phoneNumber)) {
            throw new IllegalArgumentException("手机号已被其他账户使用。");
        }

        SysUser user = new SysUser();
        user.setPhoneNumber(phoneNumber);
        user.setEmail(null);
        user.setEmailVerifiedAt(null);
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setTokenVersion(0L);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setStorageQuotaBytes(initialStorageQuotaBytes);

        return IdentityAccount.from(sysUserRepository.save(user));
    }

    public IdentityLoginSession createVerifiedEmailUser(
            String email,
            String nickname,
            String password,
            long initialStorageQuotaBytes
    ) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedNickname = normalizeNickname(nickname);
        String normalizedPassword = normalizePassword(password, "密码不能为空。");

        if (normalizedPassword.length() < 6) {
            throw new IllegalArgumentException("密码长度至少为 6 位。");
        }

        if (sysUserRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("邮箱已注册，请直接登录。");
        }

        SysUser user = new SysUser();
        user.setPhoneNumber(null);
        user.setEmail(normalizedEmail);
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setNickname(normalizedNickname);
        user.setAvatarUrl(null);
        user.setPasswordHash(passwordEncoder.encode(normalizedPassword));
        user.setTokenVersion(0L);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setStorageQuotaBytes(initialStorageQuotaBytes);

        SysUser savedUser = sysUserRepository.save(user);
        return new IdentityLoginSession(tokenService.createToken(savedUser), IdentityAccount.from(savedUser));
    }

    public void resetUserPassword(Long adminUserId, Long targetUserId, AdminResetUserPasswordRequest request) {
        if (adminUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("当前接口仅用于重置其他用户密码，请使用修改密码功能。");
        }

        SysUser user = requireUser(targetUserId);
        String newPassword = normalizePassword(request.newPassword(), "新密码不能为空。");

        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度至少为 6 位。");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同。");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        invalidateTokens(user);
        sysUserRepository.save(user);
    }

    public String normalizeEmail(String value) {
        if (value == null) {
            throw new IllegalArgumentException("邮箱不能为空。");
        }

        String email = value.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty() || email.length() > 320 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("请输入有效邮箱地址。");
        }

        return email;
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

    private String normalizeOptionalPhoneNumber(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return normalizePhoneNumber(value);
    }

    private LoginIdentifier normalizeLoginIdentifier(LoginRequest request) {
        String rawIdentifier = firstPresent(request.identifier(), request.email(), request.phoneNumber());
        if (rawIdentifier == null) {
            throw new IllegalArgumentException("请输入手机号或邮箱。");
        }

        String identifier = rawIdentifier.trim();
        if (identifier.contains("@")) {
            return new LoginIdentifier(LoginIdentifierType.EMAIL, normalizeEmail(identifier));
        }

        return new LoginIdentifier(LoginIdentifierType.PHONE, normalizePhoneNumber(identifier));
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }

        return null;
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

    private String extractLocalAvatarObjectKey(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith("cos:")) {
            return null;
        }

        String objectKey = avatarUrl.substring("cos:".length()).trim();
        return objectKey.isBlank() ? null : objectKey;
    }

    private void deleteLocalAvatarQuietly(String avatarUrl) {
        String objectKey = extractLocalAvatarObjectKey(avatarUrl);

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

    private void invalidateTokens(SysUser user) {
        long currentVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
        user.setTokenVersion(currentVersion + 1);
    }

    public UserRole normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return UserRole.USER;
        }

        try {
            return UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("角色只能是 ADMIN 或 USER。");
        }
    }

    private enum LoginIdentifierType {
        PHONE,
        EMAIL
    }

    private record LoginIdentifier(LoginIdentifierType type, String value) {
    }

    public record AvatarDownloadPayload(
            String contentType,
            long contentLength,
            InputStream inputStream
    ) {
    }
}
