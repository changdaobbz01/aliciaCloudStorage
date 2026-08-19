package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.dto.IdentityLoginRequest;
import com.alicia.cloudstorage.identity.dto.IdentityLoginResponse;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.dto.VerifyEmailRegistrationRequest;
import com.alicia.cloudstorage.identity.service.IdentityAuthService;
import com.alicia.cloudstorage.identity.service.IdentityEmailRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdentityAuthController.class)
class IdentityAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityAuthService identityAuthService;

    @MockitoBean
    private IdentityEmailRegistrationService identityEmailRegistrationService;

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

    @Test
    void meReturnsCurrentIdentityUserFromBearerToken() throws Exception {
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

        when(identityAuthService.me("Bearer token")).thenReturn(user);

        mockMvc.perform(get("/api/identity/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(identityAuthService).me("Bearer token");
    }

    @Test
    void changePasswordReturnsSuccessMessage() throws Exception {
        mockMvc.perform(put("/api/identity/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .content("{\"oldPassword\":\"OldPass1\",\"newPassword\":\"NewPass1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("密码修改成功。"));

        verify(identityAuthService).changePassword(
                org.mockito.ArgumentMatchers.eq("Bearer token"),
                any()
        );
    }

    @Test
    void requestEmailRegistrationCodeReturnsGenericMessage() throws Exception {
        mockMvc.perform(post("/api/identity/auth/register/email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, "JUnit")
                        .header("X-Forwarded-For", "203.0.113.8, 10.0.0.5")
                        .content("{\"email\":\"NewUser@Example.COM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("如果邮箱可用，验证码会发送到该邮箱。"));

        verify(identityEmailRegistrationService)
                .requestRegistrationCode("NewUser@Example.COM", "203.0.113.8", "JUnit");
    }

    @Test
    void verifyEmailRegistrationReturnsTokenAndIdentityUser() throws Exception {
        IdentityUserResponse user = new IdentityUserResponse(
                8L,
                null,
                "newuser@example.com",
                LocalDateTime.of(2026, 8, 17, 15, 30),
                "New User",
                null,
                0L,
                "USER",
                "ACTIVE",
                LocalDateTime.of(2026, 8, 17, 15, 30)
        );

        when(identityEmailRegistrationService.verifyRegistration(any(VerifyEmailRegistrationRequest.class)))
                .thenReturn(new IdentityLoginResponse("token", user));

        mockMvc.perform(post("/api/identity/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "NewUser@Example.COM",
                                  "code": "123456",
                                  "nickname": "New User",
                                  "password": "Passw0rd"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token"))
                .andExpect(jsonPath("$.user.id").value(8))
                .andExpect(jsonPath("$.user.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }
}
