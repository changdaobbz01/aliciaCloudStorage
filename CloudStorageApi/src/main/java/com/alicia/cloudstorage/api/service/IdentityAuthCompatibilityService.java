package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.identity.IdentityLoginSession;
import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.LoginResponse;
import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IdentityAuthCompatibilityService {

    private final IdentityAuthGateway identityAuthGateway;
    private final CloudUserProfileService cloudUserProfileService;

    public IdentityAuthCompatibilityService(
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

    public UserProfileResponse getCurrentUser(String authorization) {
        return cloudUserProfileService.getCurrentUser(identityAuthGateway.me(authorization));
    }

    public UserProfileResponse updateCurrentUser(String authorization, UpdateProfileRequest request) {
        IdentityUserSnapshot account = identityAuthGateway.updateProfile(authorization, request);
        return cloudUserProfileService.toUserProfile(account);
    }

    public void changePassword(String authorization, ChangePasswordRequest request) {
        identityAuthGateway.changePassword(authorization, request);
    }
}
