package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.api.identity.IdentityAdminGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminIdentityPasswordCompatibilityService {

    private final IdentityAdminGateway identityAdminGateway;

    public AdminIdentityPasswordCompatibilityService(IdentityAdminGateway identityAdminGateway) {
        this.identityAdminGateway = identityAdminGateway;
    }

    public void resetUserPassword(String authorization, Long targetUserId, AdminResetUserPasswordRequest request) {
        identityAdminGateway.resetUserPassword(authorization, targetUserId, request);
    }
}
