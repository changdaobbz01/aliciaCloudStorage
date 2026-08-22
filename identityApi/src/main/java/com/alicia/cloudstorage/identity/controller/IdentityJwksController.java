package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.service.IdentityTokenService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class IdentityJwksController {

    private final IdentityTokenService identityTokenService;

    public IdentityJwksController(IdentityTokenService identityTokenService) {
        this.identityTokenService = identityTokenService;
    }

    @GetMapping("/api/identity/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return identityTokenService.jwks();
    }
}
