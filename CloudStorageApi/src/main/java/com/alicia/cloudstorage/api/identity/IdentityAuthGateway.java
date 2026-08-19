package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.RequestEmailRegistrationCodeRequest;
import com.alicia.cloudstorage.api.dto.VerifyEmailRegistrationRequest;
import com.alicia.cloudstorage.api.service.IdentityAccount;
import com.alicia.cloudstorage.api.service.IdentityLoginSession;

public interface IdentityAuthGateway {

    IdentityLoginSession login(LoginRequest request);

    IdentityAccount me(String authorization);

    void changePassword(String authorization, ChangePasswordRequest request);

    void requestEmailRegistrationCode(
            RequestEmailRegistrationCodeRequest request,
            String clientIp,
            String userAgent
    );

    IdentityLoginSession verifyEmailRegistration(VerifyEmailRegistrationRequest request);
}
