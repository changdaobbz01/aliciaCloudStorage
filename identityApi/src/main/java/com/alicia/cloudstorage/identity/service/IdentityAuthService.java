package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.identity.dto.IdentityLoginRequest;
import com.alicia.cloudstorage.identity.dto.IdentityLoginResponse;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.dto.UpdateIdentityProfileRequest;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class IdentityAuthService {

    private final IdentityUserRepository identityUserRepository;
    private final IdentityCredentialService identityCredentialService;
    private final IdentityTokenService identityTokenService;
    private final IdentityPrincipalService identityPrincipalService;

    public IdentityAuthService(
            IdentityUserRepository identityUserRepository,
            IdentityCredentialService identityCredentialService,
            IdentityTokenService identityTokenService,
            IdentityPrincipalService identityPrincipalService
    ) {
        this.identityUserRepository = identityUserRepository;
        this.identityCredentialService = identityCredentialService;
        this.identityTokenService = identityTokenService;
        this.identityPrincipalService = identityPrincipalService;
    }

    public IdentityLoginResponse login(IdentityLoginRequest request) {
        LoginIdentifier loginIdentifier = normalizeLoginIdentifier(request);
        IdentityUser user = switch (loginIdentifier.type()) {
            case EMAIL -> identityUserRepository.findByEmail(loginIdentifier.value())
                    .orElseThrow(() -> new IllegalArgumentException("账号或密码不正确。"));
            case PHONE -> identityUserRepository.findByPhoneNumber(loginIdentifier.value())
                    .orElseThrow(() -> new IllegalArgumentException("账号或密码不正确。"));
        };

        if (!identityCredentialService.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("账号或密码不正确。");
        }

        if (user.getStatus() != IdentityUserStatus.ACTIVE) {
            throw new IdentityAuthException("当前账号已停用。");
        }

        return new IdentityLoginResponse(
                identityTokenService.createToken(user),
                IdentityUserResponse.from(user)
        );
    }

    public IdentityUserResponse me(String authorizationHeader) {
        return IdentityUserResponse.from(identityPrincipalService.requireActiveUser(authorizationHeader));
    }

    @Transactional
    public IdentityUserResponse updateProfile(String authorizationHeader, UpdateIdentityProfileRequest request) {
        IdentityUser user = identityPrincipalService.requireActiveUser(authorizationHeader);
        String phoneNumber = normalizeOptionalPhoneNumber(request.phoneNumber());
        String nickname = normalizeNickname(request.nickname());
        String avatarUrl = normalizeAvatarUrl(request.avatarUrl());

        if (phoneNumber == null && user.getEmail() == null) {
            throw new IllegalArgumentException("手机号不能为空。");
        }

        if (phoneNumber != null && identityUserRepository.existsByPhoneNumberAndIdNot(phoneNumber, user.getId())) {
            throw new IllegalArgumentException("手机号已被其他账户使用。");
        }

        user.setPhoneNumber(phoneNumber);
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);

        return IdentityUserResponse.from(identityUserRepository.save(user));
    }

    @Transactional
    public void changePassword(String authorizationHeader, ChangePasswordRequest request) {
        IdentityUser user = identityPrincipalService.requireActiveUser(authorizationHeader);
        identityCredentialService.changePassword(user, request.oldPassword(), request.newPassword());
        identityUserRepository.save(user);
    }

    private LoginIdentifier normalizeLoginIdentifier(IdentityLoginRequest request) {
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

    private String normalizeEmail(String value) {
        String email = value.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty() || email.length() > 320 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("请输入有效邮箱地址。");
        }

        return email;
    }

    private String normalizePhoneNumber(String value) {
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

    private enum LoginIdentifierType {
        PHONE,
        EMAIL
    }

    private record LoginIdentifier(LoginIdentifierType type, String value) {
    }
}
