package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AdminUpdateUserQuotaRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminCloudUserProfileManagementService {

    private final IdentityAccountService identityAccountService;
    private final CloudUserProfileService cloudUserProfileService;

    public AdminCloudUserProfileManagementService(
            IdentityAccountService identityAccountService,
            CloudUserProfileService cloudUserProfileService
    ) {
        this.identityAccountService = identityAccountService;
        this.cloudUserProfileService = cloudUserProfileService;
    }

    public UserProfileResponse updateUserStorageQuota(Long userId, AdminUpdateUserQuotaRequest request) {
        CloudUserProfileService.CloudUserProfile cloudProfile =
                cloudUserProfileService.updateUserStorageQuota(userId, request);
        IdentityAccount account = identityAccountService.getUser(userId);
        return cloudUserProfileService.toUserProfile(account, cloudProfile);
    }
}
