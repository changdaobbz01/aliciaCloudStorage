package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.dto.AdminCreateIdentityUserRequest;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.service.IdentityAdminUserService;
import com.alicia.cloudstorage.identity.service.IdentityPasswordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdentityAdminUserController.class)
class IdentityAdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityAdminUserService identityAdminUserService;

    @MockitoBean
    private IdentityPasswordService identityPasswordService;

    @Test
    void listUsersReturnsIdentityUsersWithoutPasswordHash() throws Exception {
        IdentityUserResponse user = userResponse(7L, "user@example.com", "USER");

        when(identityAdminUserService.listUsers("Bearer admin-token")).thenReturn(List.of(user));

        mockMvc.perform(get("/api/identity/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].email").value("user@example.com"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());

        verify(identityAdminUserService).listUsers("Bearer admin-token");
    }

    @Test
    void createUserReturnsCreatedIdentityUser() throws Exception {
        IdentityUserResponse user = userResponse(8L, "newuser@example.com", "ADMIN");

        when(identityAdminUserService.createUser(eq("Bearer admin-token"), any(AdminCreateIdentityUserRequest.class)))
                .thenReturn(user);

        mockMvc.perform(post("/api/identity/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .content("""
                                {
                                  "email": "NewUser@Example.COM",
                                  "nickname": "New User",
                                  "password": "Passw0rd",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(identityAdminUserService).createUser(eq("Bearer admin-token"), any(AdminCreateIdentityUserRequest.class));
    }

    @Test
    void resetUserPasswordReturnsSuccessMessage() throws Exception {
        mockMvc.perform(put("/api/identity/admin/users/64/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .content("{\"newPassword\":\"ResetPass1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("用户密码已重置，旧登录状态已失效。"));

        verify(identityPasswordService).resetUserPassword(
                eq("Bearer admin-token"),
                eq(64L),
                any()
        );
    }

    private IdentityUserResponse userResponse(Long id, String email, String role) {
        return new IdentityUserResponse(
                id,
                null,
                email,
                LocalDateTime.of(2026, 8, 17, 15, 30),
                "Alicia",
                "cos:user-avatars/" + id + "/avatar.webp",
                3L,
                role,
                "ACTIVE",
                LocalDateTime.of(2026, 4, 29, 15, 30)
        );
    }
}
