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

@Service
@Transactional
public class IdentityAdminUserService {

    private final IdentityPrincipalService identityPrincipalService;
    private final IdentityUserRepository identityUserRepository;
    private final IdentityCredentialService identityCredentialService;
    private final IdentityUserInputNormalizer identityUserInputNormalizer;

    public IdentityAdminUserService(
            IdentityPrincipalService identityPrincipalService,
            IdentityUserRepository identityUserRepository,
            IdentityCredentialService identityCredentialService,
            IdentityUserInputNormalizer identityUserInputNormalizer
    ) {
        this.identityPrincipalService = identityPrincipalService;
        this.identityUserRepository = identityUserRepository;
        this.identityCredentialService = identityCredentialService;
        this.identityUserInputNormalizer = identityUserInputNormalizer;
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

        String phoneNumber = identityUserInputNormalizer.normalizeOptionalPhoneNumber(request.phoneNumber());
        String email = identityUserInputNormalizer.normalizeOptionalEmail(request.email());
        String nickname = identityUserInputNormalizer.normalizeNickname(request.nickname());
        String passwordHash = identityCredentialService.encodeInitialPassword(request.password());
        String avatarUrl = identityUserInputNormalizer.normalizeAvatarUrl(request.avatarUrl());
        IdentityUserRole role = identityUserInputNormalizer.normalizeRole(request.role());

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

}
