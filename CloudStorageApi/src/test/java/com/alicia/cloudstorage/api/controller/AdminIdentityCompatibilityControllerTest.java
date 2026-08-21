package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.auth.AuthRequestAttributes;
import com.alicia.cloudstorage.api.auth.CurrentPrincipal;
import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.identity.UserRole;
import com.alicia.cloudstorage.api.service.AdminIdentityCreationCompatibilityService;
import com.alicia.cloudstorage.api.service.AdminIdentityDirectoryCompatibilityService;
import com.alicia.cloudstorage.api.service.AdminIdentityPasswordCompatibilityService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminIdentityCompatibilityControllerTest {

    @Mock
    private AdminIdentityCreationCompatibilityService adminIdentityCreationCompatibilityService;

    @Mock
    private AdminIdentityDirectoryCompatibilityService adminIdentityDirectoryCompatibilityService;

    @Mock
    private AdminIdentityPasswordCompatibilityService adminIdentityPasswordCompatibilityService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminIdentityCompatibilityController(
                        adminIdentityCreationCompatibilityService,
                        adminIdentityDirectoryCompatibilityService,
                        adminIdentityPasswordCompatibilityService
                ))
                .build();
    }

    @Test
    void listUsersKeepsCloudAggregationRouteUndeprecated() throws Exception {
        when(adminIdentityDirectoryCompatibilityService.listUsers("Bearer admin-token"))
                .thenReturn(List.of(profile()));

        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(LegacyIdentityCompatibilityHeaders.DEPRECATION_HEADER))
                .andExpect(jsonPath("$[0].storageQuotaBytes").value(4096));
    }

    @Test
    void resetPasswordMarksLegacyRouteWithIdentitySuccessor() throws Exception {
        mockMvc.perform(put("/api/admin/users/77/password")
                        .requestAttr(AuthRequestAttributes.CURRENT_PRINCIPAL, new CurrentPrincipal(1L, UserRole.ADMIN))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"new-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(LegacyIdentityCompatibilityHeaders.DEPRECATION_HEADER, "true"))
                .andExpect(header().string(HttpHeaders.LINK, "</api/identity/admin/users/77/password>; rel=\"successor-version\""))
                .andExpect(jsonPath("$.message").value("用户密码已重置，旧登录状态已失效。"));

        verify(adminIdentityPasswordCompatibilityService)
                .resetUserPassword(eq("Bearer admin-token"), eq(77L), any(AdminResetUserPasswordRequest.class));
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
