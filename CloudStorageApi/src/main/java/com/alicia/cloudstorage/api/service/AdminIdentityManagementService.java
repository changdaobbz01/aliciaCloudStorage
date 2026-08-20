package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.identity.IdentityAccount;
import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityAdminGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AdminIdentityManagementService {

    private final IdentityAdminGateway identityAdminGateway;
    private final CloudUserProfileService cloudUserProfileService;

    public AdminIdentityManagementService(
            IdentityAdminGateway identityAdminGateway,
            CloudUserProfileService cloudUserProfileService
    ) {
        this.identityAdminGateway = identityAdminGateway;
        this.cloudUserProfileService = cloudUserProfileService;
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> listUsers(String authorization) {
        return identityAdminGateway.listUsers(authorization).stream()
                .map(cloudUserProfileService::toUserProfile)
                .toList();
    }

    public UserProfileResponse createUser(String authorization, Long adminUserId, AdminCreateUserRequest request) {
        IdentityAccount account = identityAdminGateway.createUser(authorization, request);
        CloudUserProfileService.CloudUserProfile cloudProfile =
                cloudUserProfileService.initializeAdminCreatedUserProfile(
                        adminUserId,
                        account,
                        request.storageQuotaBytes(),
                        request.inheritAdminBackground()
                );

        return cloudUserProfileService.toUserProfile(account, cloudProfile);
    }

    public void resetUserPassword(String authorization, Long targetUserId, AdminResetUserPasswordRequest request) {
        identityAdminGateway.resetUserPassword(authorization, targetUserId, request);
    }
}
