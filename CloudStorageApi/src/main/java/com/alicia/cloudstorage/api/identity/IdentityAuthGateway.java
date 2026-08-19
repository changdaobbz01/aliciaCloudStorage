package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;

public interface IdentityAuthGateway {

    void changePassword(String authorization, ChangePasswordRequest request);
}
