package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CloudCurrentUserService {

    private final IdentityAuthGateway identityAuthGateway;
    private final CloudUserProfileService cloudUserProfileService;

    public CloudCurrentUserService(
            IdentityAuthGateway identityAuthGateway,
            CloudUserProfileService cloudUserProfileService
    ) {
        this.identityAuthGateway = identityAuthGateway;
        this.cloudUserProfileService = cloudUserProfileService;
    }

    public UserProfileResponse getCurrentUser(String authorization) {
        return cloudUserProfileService.getCurrentUser(identityAuthGateway.me(authorization));
    }
}
