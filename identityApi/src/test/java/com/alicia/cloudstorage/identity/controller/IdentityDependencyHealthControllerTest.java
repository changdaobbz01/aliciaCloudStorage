package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.service.IdentityDependencyCheck;
import com.alicia.cloudstorage.identity.service.IdentityDependencyHealth;
import com.alicia.cloudstorage.identity.service.IdentityDependencyHealthService;
import com.alicia.cloudstorage.identity.service.IdentityFlywayDependencyCheck;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdentityDependencyHealthController.class)
class IdentityDependencyHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityDependencyHealthService identityDependencyHealthService;

    @Test
    void dependenciesReturnsOkWhenIdentityDependenciesAreAvailable() throws Exception {
        when(identityDependencyHealthService.check())
                .thenReturn(IdentityDependencyHealth.of(
                        IdentityDependencyCheck.ok(),
                        IdentityFlywayDependencyCheck.ok("1")
                ));

        mockMvc.perform(get("/api/identity/health/dependencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("alicia-identity-api"))
                .andExpect(jsonPath("$.dependencies.database.available").value(true))
                .andExpect(jsonPath("$.dependencies.database.status").value("ok"))
                .andExpect(jsonPath("$.dependencies.flyway.available").value(true))
                .andExpect(jsonPath("$.dependencies.flyway.status").value("ok"))
                .andExpect(jsonPath("$.dependencies.flyway.historyTable").value("identity_flyway_schema_history"))
                .andExpect(jsonPath("$.dependencies.flyway.latestVersion").value("1"))
                .andExpect(jsonPath("$.timestamp", matchesPattern(
                        "^\\d{4}-\\d{2}-\\d{2}T.*(?:Z|[+-]\\d{2}:\\d{2})$"
                )));
    }

    @Test
    void dependenciesReturnsServiceUnavailableWhenIdentityDependenciesAreUnavailable() throws Exception {
        when(identityDependencyHealthService.check())
                .thenReturn(IdentityDependencyHealth.of(
                        IdentityDependencyCheck.unavailable(),
                        IdentityFlywayDependencyCheck.unavailable()
                ));

        mockMvc.perform(get("/api/identity/health/dependencies"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("degraded"))
                .andExpect(jsonPath("$.dependencies.database.available").value(false))
                .andExpect(jsonPath("$.dependencies.flyway.available").value(false));
    }
}
