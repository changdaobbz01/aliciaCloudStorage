package com.alicia.cloudstorage.identity.service;

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
    private final IdentityAuditLogService identityAuditLogService;

    public IdentityAuthService(
            IdentityUserRepository identityUserRepository,
            IdentityCredentialService identityCredentialService,
            IdentityTokenService identityTokenService,
            IdentityPrincipalService identityPrincipalService,
            IdentityUserInputNormalizer identityUserInputNormalizer,
            IdentityAuditLogService identityAuditLogService
    ) {
        this.identityUserRepository = identityUserRepository;
        this.identityCredentialService = identityCredentialService;
        this.identityTokenService = identityTokenService;
        this.identityPrincipalService = identityPrincipalService;
        this.identityUserInputNormalizer = identityUserInputNormalizer;
        this.identityAuditLogService = identityAuditLogService;
    }

    public IdentityLoginResponse login(IdentityLoginRequest request) {
        LoginIdentifier loginIdentifier = null;
        try {
            loginIdentifier = normalizeLoginIdentifier(request);
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

            IdentityLoginResponse response = new IdentityLoginResponse(
                    identityTokenService.createToken(user),
                    IdentityUserResponse.from(user)
            );
            identityAuditLogService.record(
                    IdentityAuditEventType.LOGIN,
                    IdentityAuditOutcome.SUCCESS,
                    user.getId(),
                    user.getId(),
                    loginIdentifier.value(),
                    null
            );
            return response;
        } catch (RuntimeException ex) {
            identityAuditLogService.record(
                    IdentityAuditEventType.LOGIN,
                    IdentityAuditOutcome.FAILURE,
                    null,
                    null,
                    loginIdentifier == null ? rawLoginIdentifier(request) : loginIdentifier.value(),
                    ex.getMessage()
            );
            throw ex;
        }
    }

    public IdentityUserResponse me(String authorizationHeader) {
        return IdentityUserResponse.from(identityPrincipalService.requireActiveUser(authorizationHeader));
    }

    public IdentityLoginResponse refreshToken(String authorizationHeader) {
        IdentityUser user = null;
        try {
            user = identityPrincipalService.requireActiveUser(authorizationHeader);
            IdentityLoginResponse response = new IdentityLoginResponse(
                    identityTokenService.createToken(user),
                    IdentityUserResponse.from(user)
            );
            identityAuditLogService.record(
                    IdentityAuditEventType.TOKEN_REFRESH,
                    IdentityAuditOutcome.SUCCESS,
                    user.getId(),
                    user.getId(),
                    userIdentifier(user),
                    null
            );
            return response;
        } catch (RuntimeException ex) {
            identityAuditLogService.record(
                    IdentityAuditEventType.TOKEN_REFRESH,
                    IdentityAuditOutcome.FAILURE,
                    user == null ? null : user.getId(),
                    user == null ? null : user.getId(),
                    userIdentifier(user),
                    ex.getMessage()
            );
            throw ex;
        }
    }

    @Transactional
    public void logout(String authorizationHeader) {
        IdentityUser user = null;
        try {
            user = identityPrincipalService.requireActiveUser(authorizationHeader);
            user.incrementTokenVersion();
            identityUserRepository.save(user);
            identityAuditLogService.record(
                    IdentityAuditEventType.LOGOUT,
                    IdentityAuditOutcome.SUCCESS,
                    user.getId(),
                    user.getId(),
                    userIdentifier(user),
                    null
            );
        } catch (RuntimeException ex) {
            identityAuditLogService.record(
                    IdentityAuditEventType.LOGOUT,
                    IdentityAuditOutcome.FAILURE,
                    user == null ? null : user.getId(),
                    user == null ? null : user.getId(),
                    userIdentifier(user),
                    ex.getMessage()
            );
            throw ex;
        }
    }

    private String rawLoginIdentifier(IdentityLoginRequest request) {
        if (request == null) {
            return null;
        }

        return firstPresent(request.identifier(), request.email(), request.phoneNumber());
    }

    private String userIdentifier(IdentityUser user) {
        if (user == null) {
            return null;
        }

        return firstPresent(user.getEmail(), user.getPhoneNumber());
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
