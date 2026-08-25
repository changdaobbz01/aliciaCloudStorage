package com.alicia.cloudstorage.rag.controller;

import com.alicia.cloudstorage.rag.health.RagDependencyHealth;
import com.alicia.cloudstorage.rag.health.RagDependencyOperationSnapshot;
import com.alicia.cloudstorage.rag.health.RagIdentityDependencyHealthService;
import com.alicia.cloudstorage.rag.health.RagStorageDependencyHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DependencyHealthControllerTest {

    @Test
    void dependenciesReturnsOkWhenIdentityAndStorageAreAvailable() {
        RagIdentityDependencyHealthService identityHealthService = mock(RagIdentityDependencyHealthService.class);
        RagStorageDependencyHealthService storageHealthService = mock(RagStorageDependencyHealthService.class);
        when(identityHealthService.check())
                .thenReturn(RagDependencyHealth.available(
                        "alicia-identity-api",
                        List.of(snapshot("identity.auth.me"))
                ));
        when(storageHealthService.check())
                .thenReturn(RagDependencyHealth.available(
                        "alicia-cloud-storage-api",
                        List.of(snapshot("storage.health"))
                ));
        DependencyHealthController controller = new DependencyHealthController(
                identityHealthService,
                storageHealthService
        );

        ResponseEntity<Map<String, Object>> response = controller.dependencies();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("status", "ok")
                .containsEntry("service", "rag-service")
                .containsKey("timestamp");
        Map<?, ?> dependencies = (Map<?, ?>) response.getBody().get("dependencies");
        RagDependencyHealth identity = (RagDependencyHealth) dependencies.get("identity");
        RagDependencyHealth storage = (RagDependencyHealth) dependencies.get("storage");
        assertThat(identity.available()).isTrue();
        assertThat(identity.service()).isEqualTo("alicia-identity-api");
        assertThat(identity.operations()).first()
                .satisfies(snapshot -> {
                    assertThat(snapshot.operation()).isEqualTo("identity.auth.me");
                    assertThat(snapshot.successCount()).isEqualTo(3L);
                });
        assertThat(storage.available()).isTrue();
        assertThat(storage.service()).isEqualTo("alicia-cloud-storage-api");
        assertThat(storage.operations()).first()
                .satisfies(snapshot -> assertThat(snapshot.operation()).isEqualTo("storage.health"));
    }

    @Test
    void dependenciesReturnsServiceUnavailableWhenAnyDependencyIsUnavailable() {
        RagIdentityDependencyHealthService identityHealthService = mock(RagIdentityDependencyHealthService.class);
        RagStorageDependencyHealthService storageHealthService = mock(RagStorageDependencyHealthService.class);
        when(identityHealthService.check())
                .thenReturn(RagDependencyHealth.available("alicia-identity-api", List.of()));
        when(storageHealthService.check())
                .thenReturn(RagDependencyHealth.unavailable("alicia-cloud-storage-api", List.of()));
        DependencyHealthController controller = new DependencyHealthController(
                identityHealthService,
                storageHealthService
        );

        ResponseEntity<Map<String, Object>> response = controller.dependencies();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "degraded");
        Map<?, ?> dependencies = (Map<?, ?>) response.getBody().get("dependencies");
        assertThat(((RagDependencyHealth) dependencies.get("identity")).available()).isTrue();
        assertThat(((RagDependencyHealth) dependencies.get("storage")).available()).isFalse();
    }

    private static RagDependencyOperationSnapshot snapshot(String operation) {
        return new RagDependencyOperationSnapshot(
                operation,
                3L,
                1L,
                4L,
                0L,
                "success",
                null,
                null,
                12L,
                15L,
                28L,
                LocalDateTime.of(2026, 8, 25, 15, 30),
                LocalDateTime.of(2026, 8, 25, 15, 30),
                LocalDateTime.of(2026, 8, 25, 15, 29)
        );
    }
}
