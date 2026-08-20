package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.AdminCreateIdentityUserRequest;
import com.alicia.cloudstorage.identity.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class IdentityAdminUserService {

    private final IdentityPrincipalService identityPrincipalService;
    private final IdentityUserRepository identityUserRepository;
    private final IdentityCredentialService identityCredentialService;

    public IdentityAdminUserService(
            IdentityPrincipalService identityPrincipalService,
            IdentityUserRepository identityUserRepository,
            IdentityCredentialService identityCredentialService
    ) {
        this.identityPrincipalService = identityPrincipalService;
        this.identityUserRepository = identityUserRepository;
        this.identityCredentialService = identityCredentialService;
    }

    @Transactional(readOnly = true)
    public List<IdentityUserResponse> listUsers(String authorizationHeader) {
        identityPrincipalService.requireAdminUser(authorizationHeader);
        return identityUserRepository.findAllByOrderByIdAsc().stream()
                .map(IdentityUserResponse::from)
                .toList();
    }

    public IdentityUserResponse createUser(
            String authorizationHeader,
            AdminCreateIdentityUserRequest request
    ) {
        identityPrincipalService.requireAdminUser(authorizationHeader);

        String phoneNumber = normalizeOptionalPhoneNumber(request.phoneNumber());
        String email = normalizeOptionalEmail(request.email());
        String nickname = normalizeNickname(request.nickname());
        String passwordHash = identityCredentialService.encodeInitialPassword(request.password());
        String avatarUrl = normalizeAvatarUrl(request.avatarUrl());
        IdentityUserRole role = normalizeRole(request.role());

        if (phoneNumber == null && email == null) {
            throw new IllegalArgumentException("手机号或邮箱不能为空。");
        }

        if (phoneNumber != null && identityUserRepository.existsByPhoneNumber(phoneNumber)) {
            throw new IllegalArgumentException("手机号已被其他账户使用。");
        }

        if (email != null && identityUserRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已注册，请直接登录。");
        }

        IdentityUser user = new IdentityUser();
        user.setPhoneNumber(phoneNumber);
        user.setEmail(email);
        user.setEmailVerifiedAt(null);
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user.setPasswordHash(passwordHash);
        user.setTokenVersion(0L);
        user.setRole(role);
        user.setStatus(IdentityUserStatus.ACTIVE);

        return IdentityUserResponse.from(identityUserRepository.save(user));
    }

    public void resetUserPassword(
            String authorizationHeader,
            Long targetUserId,
            AdminResetUserPasswordRequest request
    ) {
        IdentityUser adminUser = identityPrincipalService.requireAdminUser(authorizationHeader);
        if (adminUser.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("当前接口仅用于重置其他用户密码，请使用修改密码功能。");
        }

        IdentityUser targetUser = identityUserRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
        identityCredentialService.resetPassword(targetUser, request.newPassword());
        identityUserRepository.save(targetUser);
    }

    private String normalizeNickname(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("昵称不能为空。");
        }

        return value.trim();
    }

    private String normalizeOptionalPhoneNumber(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String phoneNumber = value.trim();
        if (!phoneNumber.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("请输入 11 位手机号。");
        }

        return phoneNumber;
    }

    private String normalizeOptionalEmail(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String email = value.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 320 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("请输入有效邮箱地址。");
        }

        return email;
    }

    private String normalizeAvatarUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }

    private IdentityUserRole normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return IdentityUserRole.USER;
        }

        try {
            return IdentityUserRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("角色只能是 ADMIN 或 USER。");
        }
    }

}
