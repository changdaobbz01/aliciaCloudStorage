package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.service.IdentityUserQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityInternalUserController {

    private final IdentityUserQueryService identityUserQueryService;

    public IdentityInternalUserController(IdentityUserQueryService identityUserQueryService) {
        this.identityUserQueryService = identityUserQueryService;
    }

    @GetMapping("/api/identity/internal/users/{userId}")
    public ResponseEntity<IdentityUserResponse> getUser(@PathVariable Long userId) {
        return identityUserQueryService.findUser(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
