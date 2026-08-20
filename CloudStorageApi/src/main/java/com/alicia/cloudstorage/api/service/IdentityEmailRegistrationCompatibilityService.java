package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.identity.IdentityLoginSession;
import com.alicia.cloudstorage.api.dto.LoginResponse;
import com.alicia.cloudstorage.api.dto.RequestEmailRegistrationCodeRequest;
import com.alicia.cloudstorage.api.dto.VerifyEmailRegistrationRequest;
import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IdentityEmailRegistrationCompatibilityService {

    private final IdentityAuthGateway identityAuthGateway;
    private final CloudUserProfileService cloudUserProfileService;

    /**
     * 适配旧版邮箱注册接口，身份创建由 identityApi 完成，云盘只初始化 profile。
     */
    public IdentityEmailRegistrationCompatibilityService(
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
                cloudUserProfileService.initializeDefaultNewUserProfile(session.user());
        return new LoginResponse(
                session.token(),
                cloudUserProfileService.toUserProfile(session.user(), cloudProfile)
        );
    }
}
