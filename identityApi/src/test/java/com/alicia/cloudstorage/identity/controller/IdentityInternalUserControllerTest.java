package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.service.IdentityUserQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdentityInternalUserController.class)
class IdentityInternalUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityUserQueryService identityUserQueryService;

    @Test
    void getUserReturnsReadOnlyIdentityProfileWithoutPasswordHash() throws Exception {
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

        when(identityUserQueryService.findUser(7L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/identity/internal/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.nickname").value("Alicia"))
                .andExpect(jsonPath("$.tokenVersion").value(3))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void getUserReturnsNotFoundWhenIdentityUserDoesNotExist() throws Exception {
        when(identityUserQueryService.findUser(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/identity/internal/users/99"))
                .andExpect(status().isNotFound());
    }
}
