package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.dto.LoginResponse;
import com.alicia.cloudstorage.api.dto.RequestEmailRegistrationCodeRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.dto.VerifyEmailRegistrationRequest;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;
import com.alicia.cloudstorage.api.identity.IdentityAuthGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityRegistrationCompatibilityServiceTest {

    @Mock
    private IdentityAuthGateway identityAuthGateway;

    @Mock
    private CloudUserProfileService cloudUserProfileService;

    @InjectMocks
    private IdentityRegistrationCompatibilityService service;

    @Test
    void requestRegistrationCodeDelegatesToIdentityApiGatewayWithClientMetadata() {
        service.requestRegistrationCode("NewUser@Example.COM", "203.0.113.8", "JUnit");

        ArgumentCaptor<RequestEmailRegistrationCodeRequest> requestCaptor =
                ArgumentCaptor.forClass(RequestEmailRegistrationCodeRequest.class);
        verify(identityAuthGateway).requestEmailRegistrationCode(
                requestCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("203.0.113.8"),
                org.mockito.ArgumentMatchers.eq("JUnit")
        );
        assertThat(requestCaptor.getValue().email()).isEqualTo("NewUser@Example.COM");
    }

    @Test
    void verifyRegistrationInitializesCloudProfileAfterIdentityApiRegistration() {
        VerifyEmailRegistrationRequest request =
                new VerifyEmailRegistrationRequest("NewUser@Example.COM", "123456", "New User", "Passw0rd");
        IdentityAccount account = new IdentityAccount(
                88L,
                null,
                "newuser@example.com",
                "New User",
                null,
                UserRole.USER,
                UserStatus.ACTIVE,
                LocalDateTime.of(2026, 8, 17, 15, 30)
        );
        CloudUserProfileService.CloudUserProfile cloudProfile =
                new CloudUserProfileService.CloudUserProfile(88L, null, 4096L);
        UserProfileResponse userProfile = new UserProfileResponse(
                88L,
                "",
                "newuser@example.com",
                "New User",
                null,
                null,
                "USER",
                "ACTIVE",
                account.createdAt(),
                4096L,
                0L,
                4096L
        );

        when(identityAuthGateway.verifyEmailRegistration(request))
                .thenReturn(new IdentityLoginSession("token", account));
        when(cloudUserProfileService.initializeDefaultNewUserProfile(account)).thenReturn(cloudProfile);
        when(cloudUserProfileService.toUserProfile(account, cloudProfile)).thenReturn(userProfile);

        LoginResponse response = service.verifyRegistration(request);

        assertThat(response.token()).isEqualTo("token");
        assertThat(response.user()).isSameAs(userProfile);
        verify(identityAuthGateway).verifyEmailRegistration(request);
        verify(cloudUserProfileService).initializeDefaultNewUserProfile(account);
    }
}
