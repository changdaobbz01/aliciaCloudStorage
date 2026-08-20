package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.LoginResponse;
import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import com.alicia.cloudstorage.api.identity.IdentityLoginSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IdentitySessionCompatibilityService {

    private final IdentityAuthGateway identityAuthGateway;
    private final CloudUserProfileService cloudUserProfileService;

    public IdentitySessionCompatibilityService(
            IdentityAuthGateway identityAuthGateway,
            CloudUserProfileService cloudUserProfileService
    ) {
        this.identityAuthGateway = identityAuthGateway;
        this.cloudUserProfileService = cloudUserProfileService;
    }

    public LoginResponse login(LoginRequest request) {
        IdentityLoginSession session = identityAuthGateway.login(request);
        return new LoginResponse(session.token(), cloudUserProfileService.toUserProfile(session.user()));
    }
}
