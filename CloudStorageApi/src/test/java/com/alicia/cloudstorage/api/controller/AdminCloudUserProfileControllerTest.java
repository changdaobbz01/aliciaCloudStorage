package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.dto.AdminUpdateUserQuotaRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.service.AdminCloudUserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminCloudUserProfileControllerTest {

    @Mock
    private AdminCloudUserProfileService adminCloudUserProfileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminCloudUserProfileController(adminCloudUserProfileService))
                .build();
    }

    @Test
    void updateUserQuotaUsesCloudUserAdminRoute() throws Exception {
        when(adminCloudUserProfileService.updateUserStorageQuota(
                eq(77L),
                any(AdminUpdateUserQuotaRequest.class)
        )).thenReturn(profile());

        mockMvc.perform(put("/api/admin/cloud-users/77/quota")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storageQuotaBytes\":4096}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.storageQuotaBytes").value(4096));

        verify(adminCloudUserProfileService).updateUserStorageQuota(
                eq(77L),
                any(AdminUpdateUserQuotaRequest.class)
        );
    }

    @Test
    void updateUserQuotaDoesNotUseIdentityUserRoute() throws Exception {
        mockMvc.perform(put("/api/admin/users/77/quota")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storageQuotaBytes\":4096}"))
                .andExpect(status().isNotFound());
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
