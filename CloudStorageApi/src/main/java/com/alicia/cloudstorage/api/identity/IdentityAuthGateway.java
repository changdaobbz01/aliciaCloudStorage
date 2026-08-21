package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;

public interface IdentityAuthGateway {

    IdentityUserSnapshot me(String authorization);

    IdentityUserSnapshot updateProfile(String authorization, UpdateProfileRequest request);
}
