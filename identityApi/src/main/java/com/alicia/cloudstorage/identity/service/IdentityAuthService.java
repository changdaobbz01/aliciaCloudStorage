package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.identity.dto.IdentityLoginRequest;
import com.alicia.cloudstorage.identity.dto.IdentityLoginResponse;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.entity.IdentityUserStatus;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IdentityAuthService {

    private final IdentityUserRepository identityUserRepository;
    private final IdentityCredentialService identityCredentialService;
    private final IdentityTokenService identityTokenService;
    private final IdentityPrincipalService identityPrincipalService;
    private final IdentityUserInputNormalizer identityUserInputNormalizer;

    public IdentityAuthService(
            IdentityUserRepository identityUserRepository,
            IdentityCredentialService identityCredentialService,
            IdentityTokenService identityTokenService,
            IdentityPrincipalService identityPrincipalService,
            IdentityUserInputNormalizer identityUserInputNormalizer
    ) {
        this.identityUserRepository = identityUserRepository;
        this.identityCredentialService = identityCredentialService;
        this.identityTokenService = identityTokenService;
        this.identityPrincipalService = identityPrincipalService;
        this.identityUserInputNormalizer = identityUserInputNormalizer;
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
            return new LoginIdentifier(LoginIdentifierType.EMAIL, identityUserInputNormalizer.normalizeEmail(identifier));
        }

        return new LoginIdentifier(LoginIdentifierType.PHONE, identityUserInputNormalizer.normalizePhoneNumber(identifier));
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }

        return null;
    }

    private enum LoginIdentifierType {
        PHONE,
        EMAIL
    }

    private record LoginIdentifier(LoginIdentifierType type, String value) {
    }
}
