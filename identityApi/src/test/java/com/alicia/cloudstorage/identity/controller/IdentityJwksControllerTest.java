package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.service.IdentityTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdentityJwksController.class)
class IdentityJwksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityTokenService identityTokenService;

    @Test
    void jwksReturnsConfiguredPublicKeys() throws Exception {
        when(identityTokenService.jwks()).thenReturn(Map.of(
                "keys",
                List.of(Map.of(
                        "kty", "RSA",
                        "use", "sig",
                        "kid", "alicia-rs256-v1",
                        "alg", "RS256",
                        "n", "modulus",
                        "e", "AQAB"
                ))
        ));

        mockMvc.perform(get("/api/identity/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].kid").value("alicia-rs256-v1"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].n").value("modulus"))
                .andExpect(jsonPath("$.keys[0].e").value("AQAB"));
    }
}
