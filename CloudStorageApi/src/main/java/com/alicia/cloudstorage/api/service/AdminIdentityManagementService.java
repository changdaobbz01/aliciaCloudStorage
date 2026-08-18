package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.entity.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AdminIdentityManagementService {

    private final IdentityAccountService identityAccountService;
    private final CloudUserProfileService cloudUserProfileService;

    public AdminIdentityManagementService(
            IdentityAccountService identityAccountService,
            CloudUserProfileService cloudUserProfileService
    ) {
        this.identityAccountService = identityAccountService;
        this.cloudUserProfileService = cloudUserProfileService;
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> listUsers() {
        return identityAccountService.listUsers().stream()
                .map(cloudUserProfileService::toUserProfile)
                .toList();
    }

    public UserProfileResponse createUser(Long adminUserId, AdminCreateUserRequest request) {
        UserRole role = identityAccountService.normalizeRole(request.role());
        long storageQuotaBytes = cloudUserProfileService.resolveInitialStorageQuota(role, request.storageQuotaBytes());
        IdentityAccount account = identityAccountService.createUser(request, storageQuotaBytes);
        CloudUserProfileService.CloudUserProfile cloudProfile =
                cloudUserProfileService.inheritAdminHomeBackground(
                        adminUserId,
                        request.inheritAdminBackground(),
                        account.id()
                );

        return cloudUserProfileService.toUserProfile(account, cloudProfile);
    }

    public void resetUserPassword(Long adminUserId, Long targetUserId, AdminResetUserPasswordRequest request) {
        identityAccountService.resetUserPassword(adminUserId, targetUserId, request);
    }
}
