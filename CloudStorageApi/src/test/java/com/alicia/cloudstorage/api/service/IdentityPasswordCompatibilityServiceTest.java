package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdentityPasswordCompatibilityServiceTest {

    @Mock
    private IdentityAuthGateway identityAuthGateway;

    @InjectMocks
    private IdentityPasswordCompatibilityService identityPasswordCompatibilityService;

    @Test
    void changePasswordDelegatesToIdentityApiGateway() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass1", "NewPass1");

        identityPasswordCompatibilityService.changePassword("Bearer token", request);

        verify(identityAuthGateway).changePassword("Bearer token", request);
    }
}
