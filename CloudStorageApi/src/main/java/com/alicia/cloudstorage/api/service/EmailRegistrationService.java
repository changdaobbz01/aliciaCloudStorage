package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.LoginResponse;
import com.alicia.cloudstorage.api.dto.RequestEmailRegistrationCodeRequest;
import com.alicia.cloudstorage.api.dto.VerifyEmailRegistrationRequest;
import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmailRegistrationService {

    private final IdentityAuthGateway identityAuthGateway;
    private final CloudUserProfileService cloudUserProfileService;

    public EmailRegistrationService(
            IdentityAuthGateway identityAuthGateway,
            CloudUserProfileService cloudUserProfileService
    ) {
        this.identityAuthGateway = identityAuthGateway;
        this.cloudUserProfileService = cloudUserProfileService;
    }

    public void requestRegistrationCode(String rawEmail, String requestIp, String userAgent) {
        identityAuthGateway.requestEmailRegistrationCode(
                new RequestEmailRegistrationCodeRequest(rawEmail),
                requestIp,
                userAgent
        );
    }

    public LoginResponse verifyRegistration(VerifyEmailRegistrationRequest request) {
        IdentityLoginSession session = identityAuthGateway.verifyEmailRegistration(request);
        CloudUserProfileService.CloudUserProfile cloudProfile =
                cloudUserProfileService.initializeDefaultNewUserProfile(session.account());
        return new LoginResponse(
                session.token(),
                cloudUserProfileService.toUserProfile(session.account(), cloudProfile)
        );
    }
}
