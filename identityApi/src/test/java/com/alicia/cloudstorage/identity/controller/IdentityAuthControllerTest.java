package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.dto.IdentityLoginRequest;
import com.alicia.cloudstorage.identity.dto.IdentityLoginResponse;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.service.IdentityAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdentityAuthController.class)
class IdentityAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityAuthService identityAuthService;

    @Test
    void loginReturnsTokenAndIdentityUserWithoutPasswordHash() throws Exception {
        IdentityUserResponse user = new IdentityUserResponse(
                7L,
                null,
                "user@example.com",
                LocalDateTime.of(2026, 8, 17, 15, 30),
                "Alicia",
                "cos:user-avatars/7/avatar.webp",
                3L,
                "USER",
                "ACTIVE",
                LocalDateTime.of(2026, 4, 29, 15, 30)
        );

        when(identityAuthService.login(any(IdentityLoginRequest.class)))
                .thenReturn(new IdentityLoginResponse("token", user));

        mockMvc.perform(post("/api/identity/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"user@example.com\",\"password\":\"Passw0rd\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token"))
                .andExpect(jsonPath("$.user.id").value(7))
                .andExpect(jsonPath("$.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }
}
