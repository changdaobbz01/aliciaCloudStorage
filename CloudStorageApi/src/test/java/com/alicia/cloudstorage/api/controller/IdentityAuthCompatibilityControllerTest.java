package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.auth.AuthRequestAttributes;
import com.alicia.cloudstorage.api.auth.CurrentPrincipal;
import com.alicia.cloudstorage.api.dto.ChangePasswordRequest;
import com.alicia.cloudstorage.api.dto.LoginRequest;
import com.alicia.cloudstorage.api.dto.LoginResponse;
import com.alicia.cloudstorage.api.dto.UpdateProfileRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.service.IdentityAvatarCompatibilityService;
import com.alicia.cloudstorage.api.service.IdentityEmailRegistrationCompatibilityService;
import com.alicia.cloudstorage.api.service.IdentityPasswordCompatibilityService;
import com.alicia.cloudstorage.api.service.IdentityProfileCompatibilityService;
import com.alicia.cloudstorage.api.service.IdentitySessionCompatibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IdentityAuthCompatibilityControllerTest {

    @Mock
    private IdentityAvatarCompatibilityService identityAvatarCompatibilityService;

    @Mock
    private IdentityEmailRegistrationCompatibilityService identityEmailRegistrationCompatibilityService;

    @Mock
    private IdentityPasswordCompatibilityService identityPasswordCompatibilityService;

    @Mock
    private IdentityProfileCompatibilityService identityProfileCompatibilityService;

    @Mock
    private IdentitySessionCompatibilityService identitySessionCompatibilityService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new IdentityAuthCompatibilityController(
                        identityAvatarCompatibilityService,
                        identityEmailRegistrationCompatibilityService,
                        identityPasswordCompatibilityService,
                        identityProfileCompatibilityService,
                        identitySessionCompatibilityService
                ))
                .build();
    }

    @Test
    void loginMarksLegacyRouteWithIdentitySuccessor() throws Exception {
        when(identitySessionCompatibilityService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse("token", profile()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"user@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(LegacyIdentityCompatibilityHeaders.DEPRECATION_HEADER, "true"))
                .andExpect(header().string(HttpHeaders.LINK, "</api/identity/auth/login>; rel=\"successor-version\""))
                .andExpect(jsonPath("$.token").value("token"));
    }

    @Test
    void currentUserKeepsCloudAggregationRouteUndeprecated() throws Exception {
        when(identityProfileCompatibilityService.getCurrentUser("Bearer token")).thenReturn(profile());

        mockMvc.perform(get("/api/auth/me")
                        .requestAttr(AuthRequestAttributes.CURRENT_PRINCIPAL, principal())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(LegacyIdentityCompatibilityHeaders.DEPRECATION_HEADER))
                .andExpect(jsonPath("$.storageQuotaBytes").value(4096));
    }

    @Test
    void updateProfileMarksLegacyRouteWithIdentitySuccessorAndReturnsCloudProfile() throws Exception {
        when(identityProfileCompatibilityService.updateCurrentUser(eq("Bearer token"), any(UpdateProfileRequest.class)))
                .thenReturn(profile());

        mockMvc.perform(put("/api/auth/profile")
                        .requestAttr(AuthRequestAttributes.CURRENT_PRINCIPAL, principal())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"13900000000\",\"nickname\":\"Alicia\",\"avatarUrl\":null}"))
                .andExpect(status().isOk())
                .andExpect(header().string(LegacyIdentityCompatibilityHeaders.DEPRECATION_HEADER, "true"))
                .andExpect(header().string(HttpHeaders.LINK, "</api/identity/auth/profile>; rel=\"successor-version\""))
                .andExpect(jsonPath("$.nickname").value("Alicia"))
                .andExpect(jsonPath("$.storageQuotaBytes").value(4096));
    }

    @Test
    void changePasswordMarksLegacyRouteWithIdentitySuccessor() throws Exception {
        mockMvc.perform(put("/api/auth/password")
                        .requestAttr(AuthRequestAttributes.CURRENT_PRINCIPAL, principal())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"old-secret\",\"newPassword\":\"new-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(LegacyIdentityCompatibilityHeaders.DEPRECATION_HEADER, "true"))
                .andExpect(header().string(HttpHeaders.LINK, "</api/identity/auth/password>; rel=\"successor-version\""))
                .andExpect(jsonPath("$.message").value("密码修改成功。"));

        verify(identityPasswordCompatibilityService).changePassword(eq("Bearer token"), any(ChangePasswordRequest.class));
    }

    private CurrentPrincipal principal() {
        return new CurrentPrincipal(77L, UserRole.USER);
    }

    private UserProfileResponse profile() {
        return new UserProfileResponse(
                77L,
                "13900000000",
                "user@example.com",
                "Alicia",
                null,
                null,
                "USER",
                "ACTIVE",
                LocalDateTime.of(2026, 4, 29, 15, 30),
                4096L,
                1536L,
                2560L
        );
    }
}
