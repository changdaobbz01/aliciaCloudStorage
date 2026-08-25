package com.alicia.cloudstorage.rag.health;

import com.alicia.cloudstorage.rag.assistant.StorageApiNodeReadClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagStorageDependencyHealthServiceTest {

    @Test
    void reportsStorageAvailableWhenHealthProbeIsAvailable() {
        FakeStorageApiNodeReadClient storageApi = FakeStorageApiNodeReadClient.available();

        RagDependencyHealth health = new RagStorageDependencyHealthService(storageApi).check();

        assertThat(health.available()).isTrue();
        assertThat(health.status()).isEqualTo("ok");
        assertThat(health.service()).isEqualTo("alicia-cloud-storage-api");
        assertThat(health.operations())
                .singleElement()
                .satisfies(snapshot -> assertThat(snapshot.operation()).isEqualTo("storage.health"));
    }

    @Test
    void reportsStorageNotConfiguredBeforeProbing() {
        FakeStorageApiNodeReadClient storageApi = FakeStorageApiNodeReadClient.notConfigured();

        RagDependencyHealth health = new RagStorageDependencyHealthService(storageApi).check();

        assertThat(health.available()).isFalse();
        assertThat(health.status()).isEqualTo("not_configured");
        assertThat(storageApi.probeCalls()).isZero();
    }

    @Test
    void reportsStorageUnavailableWhenProbeFails() {
        FakeStorageApiNodeReadClient storageApi = FakeStorageApiNodeReadClient.failing();

        RagDependencyHealth health = new RagStorageDependencyHealthService(storageApi).check();

        assertThat(health.available()).isFalse();
        assertThat(health.status()).isEqualTo("unavailable");
        assertThat(health.operations())
                .singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.operation()).isEqualTo("storage.health");
                    assertThat(snapshot.failureCount()).isEqualTo(1L);
                });
    }

    private static class FakeStorageApiNodeReadClient extends StorageApiNodeReadClient {
        private final boolean configured;
        private final boolean fail;
        private final RagDependencyTelemetry telemetry;
        private int probeCalls;

        private FakeStorageApiNodeReadClient(boolean configured, boolean fail) {
            super(new ObjectMapper(), "http://storage.test", 1);
            this.configured = configured;
            this.fail = fail;
            this.telemetry = new RagDependencyTelemetry();
        }

        static FakeStorageApiNodeReadClient available() {
            return new FakeStorageApiNodeReadClient(true, false);
        }

        static FakeStorageApiNodeReadClient notConfigured() {
            return new FakeStorageApiNodeReadClient(false, false);
        }

        static FakeStorageApiNodeReadClient failing() {
            return new FakeStorageApiNodeReadClient(true, true);
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public StorageApiHealthProbe checkHealth() {
            probeCalls++;
            return telemetry.observe("storage.health", () -> {
                if (fail) {
                    throw new IllegalStateException("storage unavailable");
                }
                return StorageApiHealthProbe.available("alicia-cloud-storage-api");
            });
        }

        @Override
        public List<RagDependencyOperationSnapshot> dependencyOperations() {
            return telemetry.snapshots("storage.");
        }

        int probeCalls() {
            return probeCalls;
        }
    }
}
