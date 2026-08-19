package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.service.IdentityAdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdentityAdminUserController.class)
class IdentityAdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityAdminUserService identityAdminUserService;

    @Test
    void resetUserPasswordReturnsSuccessMessage() throws Exception {
        mockMvc.perform(put("/api/identity/admin/users/64/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .content("{\"newPassword\":\"ResetPass1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("用户密码已重置，旧登录状态已失效。"));

        verify(identityAdminUserService).resetUserPassword(
                eq("Bearer admin-token"),
                eq(64L),
                any()
        );
    }
}
