package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.service.IdentityLoginSession;

public interface IdentityAuthGateway {

    IdentityLoginSession login(LoginRequest request);

    void changePassword(String authorization, ChangePasswordRequest request);
}
