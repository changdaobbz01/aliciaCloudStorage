package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityAdminGateway;
import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminIdentityCreationCompatibilityService {

    private final IdentityAdminGateway identityAdminGateway;
    private final CloudUserProfileService cloudUserProfileService;

    public AdminIdentityCreationCompatibilityService(
            IdentityAdminGateway identityAdminGateway,
            CloudUserProfileService cloudUserProfileService
    ) {
        this.identityAdminGateway = identityAdminGateway;
        this.cloudUserProfileService = cloudUserProfileService;
    }

    public UserProfileResponse createUser(String authorization, Long adminUserId, AdminCreateUserRequest request) {
        IdentityUserSnapshot account = identityAdminGateway.createUser(authorization, request);
        CloudUserProfileService.CloudUserProfile cloudProfile =
                cloudUserProfileService.initializeAdminCreatedUserProfile(
                        adminUserId,
                        account,
                        request.storageQuotaBytes(),
                        request.inheritAdminBackground()
                );

        return cloudUserProfileService.toUserProfile(account, cloudProfile);
    }
}
