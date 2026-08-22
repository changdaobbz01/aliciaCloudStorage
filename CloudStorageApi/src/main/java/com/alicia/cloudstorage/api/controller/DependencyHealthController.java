package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.identity.IdentityDependencyHealth;
import com.alicia.cloudstorage.api.identity.IdentityDependencyHealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class DependencyHealthController {

    private final IdentityDependencyHealthService identityHealthService;

    public DependencyHealthController(IdentityDependencyHealthService identityHealthService) {
        this.identityHealthService = identityHealthService;
    }

    @GetMapping("/api/health/dependencies")
    public ResponseEntity<Map<String, Object>> dependencies() {
        IdentityDependencyHealth identity = identityHealthService.check();
        boolean healthy = identity.available();

        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", healthy ? "ok" : "degraded",
                        "service", "alicia-cloud-storage-api",
                        "dependencies", Map.of("identity", identity),
                        "timestamp", LocalDateTime.now()
                ));
    }
}
