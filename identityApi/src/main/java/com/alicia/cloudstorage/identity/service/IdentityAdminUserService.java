package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.AdminCreateIdentityUserRequest;
import com.alicia.cloudstorage.identity.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
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
    private final IdentityUserCreationService identityUserCreationService;

    public IdentityAdminUserService(
            IdentityPrincipalService identityPrincipalService,
            IdentityUserRepository identityUserRepository,
            IdentityCredentialService identityCredentialService,
            IdentityUserCreationService identityUserCreationService
    ) {
        this.identityPrincipalService = identityPrincipalService;
        this.identityUserRepository = identityUserRepository;
        this.identityCredentialService = identityCredentialService;
        this.identityUserCreationService = identityUserCreationService;
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
        return IdentityUserResponse.from(identityUserCreationService.createAdminManagedUser(request));
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
