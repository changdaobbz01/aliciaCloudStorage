package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.service.IdentityDependencyHealth;
import com.alicia.cloudstorage.identity.service.IdentityDependencyHealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@RestController
public class IdentityDependencyHealthController {

    private final IdentityDependencyHealthService identityDependencyHealthService;

    public IdentityDependencyHealthController(IdentityDependencyHealthService identityDependencyHealthService) {
        this.identityDependencyHealthService = identityDependencyHealthService;
    }

    @GetMapping("/api/identity/health/dependencies")
    public ResponseEntity<Map<String, Object>> dependencies() {
        IdentityDependencyHealth health = identityDependencyHealthService.check();
        boolean healthy = health.available();

        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", healthy ? "ok" : "degraded",
                        "service", "alicia-identity-api",
                        "dependencies", Map.of(
                                "database", health.database(),
                                "flyway", health.flyway()
                        ),
                        "timestamp", OffsetDateTime.now(ZoneOffset.UTC)
                ));
    }
}
