package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.config.JacksonConfiguration;
import com.alicia.cloudstorage.api.identity.IdentityDependencyHealth;
import com.alicia.cloudstorage.api.identity.IdentityGatewayOperationSnapshot;
import com.alicia.cloudstorage.api.identity.IdentityDependencyHealthService;
import com.alicia.cloudstorage.api.principal.CurrentPrincipalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DependencyHealthController.class)
@Import(JacksonConfiguration.class)
class DependencyHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentPrincipalService currentPrincipalService;

    @MockitoBean
    private IdentityDependencyHealthService identityHealthService;

    @Test
    void dependenciesReturnsOkWhenIdentityIsAvailable() throws Exception {
        when(identityHealthService.check())
                .thenReturn(IdentityDependencyHealth.available(
                        "alicia-identity-api",
                        List.of(new IdentityGatewayOperationSnapshot(
                                "auth.me",
                                12L,
                                1L,
                                "success",
                                null,
                                18L,
                                LocalDateTime.of(2026, 8, 24, 13, 30)
                        ))
                ));

        mockMvc.perform(get("/api/health/dependencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("alicia-cloud-storage-api"))
                .andExpect(jsonPath("$.dependencies.identity.available").value(true))
                .andExpect(jsonPath("$.dependencies.identity.status").value("ok"))
                .andExpect(jsonPath("$.dependencies.identity.service").value("alicia-identity-api"))
                .andExpect(jsonPath("$.dependencies.identity.operations[0].operation").value("auth.me"))
                .andExpect(jsonPath("$.dependencies.identity.operations[0].successCount").value(12))
                .andExpect(jsonPath("$.dependencies.identity.operations[0].failureCount").value(1))
                .andExpect(jsonPath("$.dependencies.identity.operations[0].lastOutcome").value("success"))
                .andExpect(jsonPath("$.dependencies.identity.operations[0].lastDurationMs").value(18))
                .andExpect(jsonPath("$.timestamp", matchesPattern(
                        "^\\d{4}-\\d{2}-\\d{2}T.*(?:Z|[+-]\\d{2}:\\d{2})$"
                )));
    }

    @Test
    void dependenciesReturnsServiceUnavailableWhenIdentityIsUnavailable() throws Exception {
        when(identityHealthService.check())
                .thenReturn(IdentityDependencyHealth.unavailable());

        mockMvc.perform(get("/api/health/dependencies"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("degraded"))
                .andExpect(jsonPath("$.dependencies.identity.available").value(false))
                .andExpect(jsonPath("$.dependencies.identity.status").value("unavailable"));
    }
}
