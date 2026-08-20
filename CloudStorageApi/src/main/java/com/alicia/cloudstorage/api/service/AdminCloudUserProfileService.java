package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import com.alicia.cloudstorage.api.dto.AdminUpdateUserQuotaRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityUserGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminCloudUserProfileService {

    private final IdentityUserGateway identityUserGateway;
    private final CloudUserProfileService cloudUserProfileService;

    public AdminCloudUserProfileService(
            IdentityUserGateway identityUserGateway,
            CloudUserProfileService cloudUserProfileService
    ) {
        this.identityUserGateway = identityUserGateway;
        this.cloudUserProfileService = cloudUserProfileService;
    }

    public UserProfileResponse updateUserStorageQuota(Long userId, AdminUpdateUserQuotaRequest request) {
        CloudUserProfileService.CloudUserProfile cloudProfile =
                cloudUserProfileService.updateUserStorageQuota(userId, request);
        IdentityUserSnapshot account = identityUserGateway.getUser(userId);
        return cloudUserProfileService.toUserProfile(account, cloudProfile);
    }
}
