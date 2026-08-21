package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.identity.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import com.alicia.cloudstorage.identity.repository.IdentityUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IdentityPasswordService {

    private final IdentityPrincipalService identityPrincipalService;
    private final IdentityUserRepository identityUserRepository;
    private final IdentityCredentialService identityCredentialService;
    private final IdentityAuditLogService identityAuditLogService;

    public IdentityPasswordService(
            IdentityPrincipalService identityPrincipalService,
            IdentityUserRepository identityUserRepository,
            IdentityCredentialService identityCredentialService,
            IdentityAuditLogService identityAuditLogService
    ) {
        this.identityPrincipalService = identityPrincipalService;
        this.identityUserRepository = identityUserRepository;
        this.identityCredentialService = identityCredentialService;
        this.identityAuditLogService = identityAuditLogService;
    }

    public void changePassword(String authorizationHeader, ChangePasswordRequest request) {
        IdentityUser user = null;
        try {
            user = identityPrincipalService.requireActiveUser(authorizationHeader);
            identityCredentialService.changePassword(user, request.oldPassword(), request.newPassword());
            identityUserRepository.save(user);
            identityAuditLogService.record(
                    IdentityAuditEventType.PASSWORD_CHANGE,
                    IdentityAuditOutcome.SUCCESS,
                    user.getId(),
                    user.getId(),
                    userIdentifier(user),
                    null
            );
        } catch (RuntimeException ex) {
            identityAuditLogService.record(
                    IdentityAuditEventType.PASSWORD_CHANGE,
                    IdentityAuditOutcome.FAILURE,
                    user == null ? null : user.getId(),
                    user == null ? null : user.getId(),
                    userIdentifier(user),
                    ex.getMessage()
            );
            throw ex;
        }
    }

    public void resetUserPassword(
            String authorizationHeader,
            Long targetUserId,
            AdminResetUserPasswordRequest request
    ) {
        IdentityUser adminUser = null;
        IdentityUser targetUser = null;
        try {
            adminUser = identityPrincipalService.requireAdminUser(authorizationHeader);
            if (adminUser.getId().equals(targetUserId)) {
                throw new IllegalArgumentException("当前接口仅用于重置其他用户密码，请使用修改密码功能。");
            }

            targetUser = identityUserRepository.findById(targetUserId)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
            identityCredentialService.resetPassword(targetUser, request.newPassword());
            identityUserRepository.save(targetUser);
            identityAuditLogService.record(
                    IdentityAuditEventType.ADMIN_PASSWORD_RESET,
                    IdentityAuditOutcome.SUCCESS,
                    adminUser.getId(),
                    targetUser.getId(),
                    userIdentifier(targetUser),
                    null
            );
        } catch (RuntimeException ex) {
            identityAuditLogService.record(
                    IdentityAuditEventType.ADMIN_PASSWORD_RESET,
                    IdentityAuditOutcome.FAILURE,
                    adminUser == null ? null : adminUser.getId(),
                    targetUser == null ? targetUserId : targetUser.getId(),
                    userIdentifier(targetUser),
                    ex.getMessage()
            );
            throw ex;
        }
    }

    private String userIdentifier(IdentityUser user) {
        if (user == null) {
            return null;
        }

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }

        return user.getPhoneNumber();
    }
}
