package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.identity.IdentityAccount;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityUserGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class CloudProfileManagementService {

    private final IdentityUserGateway identityUserGateway;
    private final CloudUserProfileService cloudUserProfileService;

    public CloudProfileManagementService(
            IdentityUserGateway identityUserGateway,
            CloudUserProfileService cloudUserProfileService
    ) {
        this.identityUserGateway = identityUserGateway;
        this.cloudUserProfileService = cloudUserProfileService;
    }

    public UserProfileResponse uploadCurrentUserHomeBackground(Long userId, MultipartFile file) {
        CloudUserProfileService.CloudUserProfile cloudProfile =
                cloudUserProfileService.uploadCurrentUserHomeBackground(userId, file);
        IdentityAccount account = identityUserGateway.getUser(userId);
        return cloudUserProfileService.toUserProfile(account, cloudProfile);
    }

    public UserProfileResponse clearCurrentUserHomeBackground(Long userId) {
        CloudUserProfileService.CloudUserProfile cloudProfile =
                cloudUserProfileService.clearCurrentUserHomeBackground(userId);
        IdentityAccount account = identityUserGateway.getUser(userId);
        return cloudUserProfileService.toUserProfile(account, cloudProfile);
    }

    @Transactional(readOnly = true)
    public CosFileStorageService.PresignedCosUrl resolveUserHomeBackgroundAccessUrl(Long userId) {
        return cloudUserProfileService.resolveUserHomeBackgroundAccessUrl(userId);
    }
}
