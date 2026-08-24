package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.IdentityLoginRequest;
import com.alicia.cloudstorage.identity.dto.IdentityLoginResponse;
import com.alicia.cloudstorage.identity.dto.IdentityLogoutRequest;
import com.alicia.cloudstorage.identity.dto.IdentityRefreshTokenRequest;
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
    private final IdentityRefreshTokenService identityRefreshTokenService;
    private final IdentityUserResponseAssembler identityUserResponseAssembler;

    public IdentityAuthService(
            IdentityUserRepository identityUserRepository,
            IdentityCredentialService identityCredentialService,
            IdentityTokenService identityTokenService,
            IdentityPrincipalService identityPrincipalService,
            IdentityUserInputNormalizer identityUserInputNormalizer,
            IdentityAuditLogService identityAuditLogService,
            IdentityRefreshTokenService identityRefreshTokenService,
            IdentityUserResponseAssembler identityUserResponseAssembler
    ) {
        this.identityUserRepository = identityUserRepository;
        this.identityCredentialService = identityCredentialService;
        this.identityTokenService = identityTokenService;
        this.identityPrincipalService = identityPrincipalService;
        this.identityUserInputNormalizer = identityUserInputNormalizer;
        this.identityAuditLogService = identityAuditLogService;
        this.identityRefreshTokenService = identityRefreshTokenService;
        this.identityUserResponseAssembler = identityUserResponseAssembler;
    }

    @Transactional
    public IdentityLoginResponse login(IdentityLoginRequest request, String clientAddress, String userAgent) {
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

            IdentityLoginResponse response = createSessionResponse(user, clientAddress, userAgent);
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
        return identityUserResponseAssembler.toResponse(identityPrincipalService.requireActiveUser(authorizationHeader));
    }

    @Transactional
    public IdentityLoginResponse refreshToken(
            IdentityRefreshTokenRequest request,
            String clientAddress,
            String userAgent
    ) {
        IdentityUser user = null;
        try {
            String refreshToken = requireRefreshToken(request);
            IdentityRefreshTokenService.RefreshedIdentitySession session =
                    identityRefreshTokenService.rotate(refreshToken, clientAddress, userAgent);
            user = session.user();
            IdentityLoginResponse response = createSessionResponse(user, session.sessionId(), session.refreshToken());
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
    public void logout(String authorizationHeader, IdentityLogoutRequest request) {
        IdentityUser user = null;
        try {
            IdentityPrincipalService.IdentityPrincipal principal =
                    identityPrincipalService.requireActivePrincipal(authorizationHeader);
            user = principal.user();
            String detail = "current_session";

            if (request != null && request.logoutAllDevices()) {
                identityRefreshTokenService.revokeAllForUser(user.getId(), "logout_all_devices");
                user.incrementTokenVersion();
                identityUserRepository.save(user);
                detail = "all_devices";
            } else if (principal.tokenClaims().refreshSessionId() != null) {
                identityRefreshTokenService.revokeSession(principal.tokenClaims().refreshSessionId(), "logout");
            } else {
                user.incrementTokenVersion();
                identityUserRepository.save(user);
                detail = "token_without_session";
            }

            identityAuditLogService.record(
                    IdentityAuditEventType.LOGOUT,
                    IdentityAuditOutcome.SUCCESS,
                    user.getId(),
                    user.getId(),
                    userIdentifier(user),
                    detail
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

    private IdentityLoginResponse createSessionResponse(IdentityUser user, String clientAddress, String userAgent) {
        IdentityRefreshTokenService.IssuedRefreshToken refreshToken =
                identityRefreshTokenService.issue(user, clientAddress, userAgent);
        return createSessionResponse(user, refreshToken.sessionId(), refreshToken.token());
    }

    private IdentityLoginResponse createSessionResponse(IdentityUser user, Long refreshSessionId, String refreshToken) {
        return new IdentityLoginResponse(
                identityTokenService.createToken(user, refreshSessionId),
                refreshToken,
                identityUserResponseAssembler.toResponse(user)
        );
    }

    private String requireRefreshToken(IdentityRefreshTokenRequest request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            throw new IdentityAuthException("刷新令牌不能为空。");
        }

        return request.refreshToken().trim();
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
