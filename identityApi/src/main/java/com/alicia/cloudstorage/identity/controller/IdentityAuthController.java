package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.dto.IdentityLoginRequest;
import com.alicia.cloudstorage.identity.dto.IdentityLoginResponse;
import com.alicia.cloudstorage.identity.service.IdentityAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/identity/auth")
public class IdentityAuthController {

    private final IdentityAuthService identityAuthService;

    public IdentityAuthController(IdentityAuthService identityAuthService) {
        this.identityAuthService = identityAuthService;
    }

    @PostMapping("/login")
    public IdentityLoginResponse login(@Valid @RequestBody IdentityLoginRequest request) {
        return identityAuthService.login(request);
    }
}
