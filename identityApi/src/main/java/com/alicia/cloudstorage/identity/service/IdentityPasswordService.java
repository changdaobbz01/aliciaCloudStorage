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

    public IdentityPasswordService(
            IdentityPrincipalService identityPrincipalService,
            IdentityUserRepository identityUserRepository,
            IdentityCredentialService identityCredentialService
    ) {
        this.identityPrincipalService = identityPrincipalService;
        this.identityUserRepository = identityUserRepository;
        this.identityCredentialService = identityCredentialService;
    }

    public void changePassword(String authorizationHeader, ChangePasswordRequest request) {
        IdentityUser user = identityPrincipalService.requireActiveUser(authorizationHeader);
        identityCredentialService.changePassword(user, request.oldPassword(), request.newPassword());
        identityUserRepository.save(user);
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
