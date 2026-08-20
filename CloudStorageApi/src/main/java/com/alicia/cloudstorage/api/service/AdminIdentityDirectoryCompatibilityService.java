package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityAdminGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminIdentityDirectoryCompatibilityService {

    private final IdentityAdminGateway identityAdminGateway;
    private final CloudUserProfileService cloudUserProfileService;

    public AdminIdentityDirectoryCompatibilityService(
            IdentityAdminGateway identityAdminGateway,
            CloudUserProfileService cloudUserProfileService
    ) {
        this.identityAdminGateway = identityAdminGateway;
        this.cloudUserProfileService = cloudUserProfileService;
    }

    public List<UserProfileResponse> listUsers(String authorization) {
        return identityAdminGateway.listUsers(authorization).stream()
                .map(cloudUserProfileService::toUserProfile)
                .toList();
    }
}
