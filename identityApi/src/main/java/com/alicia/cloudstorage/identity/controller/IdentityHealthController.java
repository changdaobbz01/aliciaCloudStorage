package com.alicia.cloudstorage.identity.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@RestController
public class IdentityHealthController {

    @GetMapping("/api/identity/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "alicia-identity-api",
                "timestamp", OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
