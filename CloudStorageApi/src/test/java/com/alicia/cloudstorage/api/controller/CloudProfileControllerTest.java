package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.principal.PrincipalRequestAttributes;
import com.alicia.cloudstorage.api.principal.CurrentPrincipal;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;
import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.identity.UserStatus;
import com.alicia.cloudstorage.api.service.CloudCurrentUserService;
import com.alicia.cloudstorage.api.service.CloudProfileManagementService;
import com.alicia.cloudstorage.api.service.CloudUserAvatarService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CloudProfileControllerTest {

    @Mock
    private CloudCurrentUserService cloudCurrentUserService;

    @Mock
    private CloudUserAvatarService cloudUserAvatarService;

    @Mock
    private CloudProfileManagementService cloudProfileManagementService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CloudProfileController(
                        cloudCurrentUserService,
                        cloudUserAvatarService,
                        cloudProfileManagementService
                ))
                .build();
    }

    @Test
    void removedAuthRoutesReturnNotFound() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"user@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/auth/register/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"code\":\"123456\",\"nickname\":\"Alicia\",\"password\":\"secret\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/auth/profile")
                        .requestAttr(PrincipalRequestAttributes.CURRENT_PRINCIPAL, principal())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"13900000000\",\"nickname\":\"Alicia\",\"avatarUrl\":null}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/auth/password")
                        .requestAttr(PrincipalRequestAttributes.CURRENT_PRINCIPAL, principal())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"old-secret\",\"newPassword\":\"new-secret\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/auth/me")
                        .requestAttr(PrincipalRequestAttributes.CURRENT_PRINCIPAL, principal())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/auth/avatar")
                        .requestAttr(PrincipalRequestAttributes.CURRENT_PRINCIPAL, principal())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void currentUserUsesCloudProfileAggregationRoute() throws Exception {
        IdentityUserSnapshot identityUser = identityUser();
        when(cloudCurrentUserService.getCurrentUser(identityUser)).thenReturn(profile());

        mockMvc.perform(get("/api/cloud-profile/me")
                .requestAttr(PrincipalRequestAttributes.CURRENT_PRINCIPAL, principal())
                .requestAttr(PrincipalRequestAttributes.CURRENT_IDENTITY_USER, identityUser)
                .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Deprecation"))
                .andExpect(jsonPath("$.storageQuotaBytes").value(4096));
    }

    @Test
    void avatarUploadReusesIdentityUserFromRequestContext() throws Exception {
        IdentityUserSnapshot identityUser = identityUser();
        when(cloudUserAvatarService.uploadCurrentUserAvatar(
                eq(identityUser),
                eq("Bearer token"),
                any()
        )).thenReturn(profile());

        mockMvc.perform(multipart("/api/cloud-profile/avatar")
                        .file("file", new byte[]{1, 2, 3})
                        .requestAttr(PrincipalRequestAttributes.CURRENT_PRINCIPAL, principal())
                        .requestAttr(PrincipalRequestAttributes.CURRENT_IDENTITY_USER, identityUser)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77));
    }

    private CurrentPrincipal principal() {
        return new CurrentPrincipal(77L, UserRole.USER);
    }

    private IdentityUserSnapshot identityUser() {
        return new IdentityUserSnapshot(
                77L,
                "13900000000",
                "user@example.com",
                "Alicia",
                null,
                UserRole.USER,
                UserStatus.ACTIVE,
                LocalDateTime.of(2026, 4, 29, 15, 30)
        );
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
