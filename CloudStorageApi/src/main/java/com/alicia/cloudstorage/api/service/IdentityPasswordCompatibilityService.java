package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IdentityPasswordCompatibilityService {

    private final IdentityAuthGateway identityAuthGateway;

    public IdentityPasswordCompatibilityService(IdentityAuthGateway identityAuthGateway) {
        this.identityAuthGateway = identityAuthGateway;
    }

    public void changePassword(String authorization, ChangePasswordRequest request) {
        identityAuthGateway.changePassword(authorization, request);
    }
}
