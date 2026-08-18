package com.alicia.cloudstorage.identity.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdentityHealthController.class)
class IdentityHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsIndependentIdentityServiceStatus() throws Exception {
        mockMvc.perform(get("/api/identity/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("alicia-identity-api"))
                .andExpect(jsonPath("$.timestamp", matchesPattern(
                        "^\\d{4}-\\d{2}-\\d{2}T.*(?:Z|[+-]\\d{2}:\\d{2})$"
                )));
    }
}
