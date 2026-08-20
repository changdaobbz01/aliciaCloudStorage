package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;

import java.util.List;

public interface IdentityAdminGateway {

    List<IdentityUserSnapshot> listUsers(String authorization);

    IdentityUserSnapshot createUser(String authorization, AdminCreateUserRequest request);

    void resetUserPassword(String authorization, Long targetUserId, AdminResetUserPasswordRequest request);
}
