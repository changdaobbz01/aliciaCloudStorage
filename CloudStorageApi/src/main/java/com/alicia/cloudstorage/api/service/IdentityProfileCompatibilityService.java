package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IdentityProfileCompatibilityService {

    private final IdentityAuthGateway identityAuthGateway;
    private final CloudUserProfileService cloudUserProfileService;

    public IdentityProfileCompatibilityService(
            IdentityAuthGateway identityAuthGateway,
            CloudUserProfileService cloudUserProfileService
    ) {
        this.identityAuthGateway = identityAuthGateway;
        this.cloudUserProfileService = cloudUserProfileService;
    }

    public UserProfileResponse getCurrentUser(String authorization) {
        return cloudUserProfileService.getCurrentUser(identityAuthGateway.me(authorization));
    }

    public UserProfileResponse updateCurrentUser(String authorization, UpdateProfileRequest request) {
        IdentityUserSnapshot account = identityAuthGateway.updateProfile(authorization, request);
        return cloudUserProfileService.toUserProfile(account);
    }
}
