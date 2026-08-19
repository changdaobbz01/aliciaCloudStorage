package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.api.service.IdentityAccount;

import java.util.List;

public interface IdentityAdminGateway {

    List<IdentityAccount> listUsers(String authorization);

    IdentityAccount createUser(String authorization, AdminCreateUserRequest request);

    void resetUserPassword(String authorization, Long targetUserId, AdminResetUserPasswordRequest request);
}
