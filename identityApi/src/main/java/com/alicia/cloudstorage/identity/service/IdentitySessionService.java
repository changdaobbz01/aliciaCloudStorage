package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.IdentitySessionResponse;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IdentitySessionService {

    private final IdentityPrincipalService identityPrincipalService;
    private final IdentityRefreshTokenService identityRefreshTokenService;
    private final IdentityAuditLogService identityAuditLogService;

    public IdentitySessionService(
            IdentityPrincipalService identityPrincipalService,
            IdentityRefreshTokenService identityRefreshTokenService,
            IdentityAuditLogService identityAuditLogService
    ) {
        this.identityPrincipalService = identityPrincipalService;
        this.identityRefreshTokenService = identityRefreshTokenService;
        this.identityAuditLogService = identityAuditLogService;
    }

    @Transactional(readOnly = true)
    public List<IdentitySessionResponse> listCurrentUserSessions(String authorizationHeader, Boolean includeRevoked) {
        IdentityPrincipalService.IdentityPrincipal principal =
                identityPrincipalService.requireActivePrincipal(authorizationHeader);
        return identityRefreshTokenService.listUserSessions(
                principal.user().getId(),
                principal.tokenClaims().refreshSessionId(),
                Boolean.TRUE.equals(includeRevoked)
        );
    }

    @Transactional
    public void revokeCurrentUserSession(String authorizationHeader, Long sessionId) {
        IdentityUser user = null;
        try {
            IdentityPrincipalService.IdentityPrincipal principal =
                    identityPrincipalService.requireActivePrincipal(authorizationHeader);
            user = principal.user();
            validateSessionId(sessionId);
            identityRefreshTokenService.revokeUserSession(user.getId(), sessionId, "user_revoke_session");
            identityAuditLogService.record(
                    IdentityAuditEventType.SESSION_REVOKE,
                    IdentityAuditOutcome.SUCCESS,
                    user.getId(),
                    user.getId(),
                    userIdentifier(user),
                    "session_revoke:" + sessionId
            );
        } catch (RuntimeException ex) {
            identityAuditLogService.record(
                    IdentityAuditEventType.SESSION_REVOKE,
                    IdentityAuditOutcome.FAILURE,
                    user == null ? null : user.getId(),
                    user == null ? null : user.getId(),
                    user == null ? null : userIdentifier(user),
                    "session_revoke:" + sessionId
            );
            throw ex;
        }
    }

    private void validateSessionId(Long sessionId) {
        if (sessionId == null || sessionId <= 0L) {
            throw new IllegalArgumentException("登录会话编号不合法。");
        }
    }

    private String userIdentifier(IdentityUser user) {
        if (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) {
            return user.getPhoneNumber();
        }

        return user.getEmail();
    }
}
