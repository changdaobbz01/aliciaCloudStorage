package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.api.identity.IdentityAdminGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminIdentityPasswordCompatibilityServiceTest {

    @Mock
    private IdentityAdminGateway identityAdminGateway;

    @InjectMocks
    private AdminIdentityPasswordCompatibilityService adminIdentityPasswordCompatibilityService;

    @Test
    void resetUserPasswordDelegatesToIdentityService() {
        AdminResetUserPasswordRequest request = new AdminResetUserPasswordRequest("ResetPass@456");

        adminIdentityPasswordCompatibilityService.resetUserPassword("Bearer admin-token", 64L, request);

        verify(identityAdminGateway).resetUserPassword("Bearer admin-token", 64L, request);
    }
}
