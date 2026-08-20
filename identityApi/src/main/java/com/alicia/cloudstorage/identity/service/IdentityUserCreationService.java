package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.AdminCreateIdentityUserRequest;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserRole;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class IdentityUserCreationService {

    private static final String DEFAULT_BOOTSTRAP_ADMIN_NICKNAME = "\u7cfb\u7edf\u7ba1\u7406\u5458";

    private final IdentityUserRepository identityUserRepository;
    private final IdentityCredentialService identityCredentialService;
    private final IdentityUserInputNormalizer identityUserInputNormalizer;

    public IdentityUserCreationService(
            IdentityUserRepository identityUserRepository,
            IdentityCredentialService identityCredentialService,
            IdentityUserInputNormalizer identityUserInputNormalizer
    ) {
        this.identityUserRepository = identityUserRepository;
        this.identityCredentialService = identityCredentialService;
        this.identityUserInputNormalizer = identityUserInputNormalizer;
    }

    public IdentityUser createAdminManagedUser(AdminCreateIdentityUserRequest request) {
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

        return saveActiveUser(phoneNumber, email, null, nickname, avatarUrl, passwordHash, role);
    }

    public IdentityUser createVerifiedEmailUser(
            String email,
            String nickname,
            String password,
            LocalDateTime verifiedAt
    ) {
        String normalizedEmail = identityUserInputNormalizer.normalizeEmail(email);
        if (identityUserRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("邮箱已注册，请直接登录。");
        }

        String normalizedNickname = identityUserInputNormalizer.normalizeNickname(nickname);
        String passwordHash = identityCredentialService.encodeInitialPassword(password);

        return saveActiveUser(
                null,
                normalizedEmail,
                verifiedAt,
                normalizedNickname,
                null,
                passwordHash,
                IdentityUserRole.USER
        );
    }

    public IdentityUser createBootstrapAdmin(
            String phoneNumber,
            String password,
            String nickname,
            String avatarUrl
    ) {
        String normalizedPhoneNumber = identityUserInputNormalizer.normalizePhoneNumber(phoneNumber);
        String normalizedNickname = normalizeBootstrapAdminNickname(nickname);
        String normalizedAvatarUrl = identityUserInputNormalizer.normalizeAvatarUrl(avatarUrl);
        String passwordHash = identityCredentialService.encodeInitialPassword(password);

        return saveActiveUser(
                normalizedPhoneNumber,
                null,
                null,
                normalizedNickname,
                normalizedAvatarUrl,
                passwordHash,
                IdentityUserRole.ADMIN
        );
    }

    private String normalizeBootstrapAdminNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return DEFAULT_BOOTSTRAP_ADMIN_NICKNAME;
        }

        return identityUserInputNormalizer.normalizeNickname(nickname);
    }

    private IdentityUser saveActiveUser(
            String phoneNumber,
            String email,
            LocalDateTime emailVerifiedAt,
            String nickname,
            String avatarUrl,
            String passwordHash,
            IdentityUserRole role
    ) {
        IdentityUser user = new IdentityUser();
        user.setPhoneNumber(phoneNumber);
        user.setEmail(email);
        user.setEmailVerifiedAt(emailVerifiedAt);
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user.setPasswordHash(passwordHash);
        user.setTokenVersion(0L);
        user.setRole(role);
        user.setStatus(IdentityUserStatus.ACTIVE);

        return identityUserRepository.save(user);
    }
}
