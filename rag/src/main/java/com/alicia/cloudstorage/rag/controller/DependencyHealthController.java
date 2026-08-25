package com.alicia.cloudstorage.rag.controller;

import com.alicia.cloudstorage.rag.health.RagDependencyHealth;
import com.alicia.cloudstorage.rag.health.RagIdentityDependencyHealthService;
import com.alicia.cloudstorage.rag.health.RagStorageDependencyHealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
public class DependencyHealthController {

    private final RagIdentityDependencyHealthService identityHealthService;
    private final RagStorageDependencyHealthService storageHealthService;

    public DependencyHealthController(
            RagIdentityDependencyHealthService identityHealthService,
            RagStorageDependencyHealthService storageHealthService
    ) {
        this.identityHealthService = identityHealthService;
        this.storageHealthService = storageHealthService;
    }

    @GetMapping("/api/health/dependencies")
    public ResponseEntity<Map<String, Object>> dependencies() {
        RagDependencyHealth identity = identityHealthService.check();
        RagDependencyHealth storage = storageHealthService.check();
        boolean healthy = identity.available() && storage.available();

        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", healthy ? "ok" : "degraded",
                        "service", "rag-service",
                        "dependencies", Map.of(
                                "identity", identity,
                                "storage", storage
                        ),
                        "timestamp", OffsetDateTime.now()
                ));
    }
}
