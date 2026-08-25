package com.alicia.cloudstorage.rag.health;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagDependencyTelemetryTest {

    private final RagDependencyTelemetry telemetry = new RagDependencyTelemetry();

    @Test
    void observeRecordsSuccessAndFailureWithoutChangingResult() {
        String result = telemetry.observe("identity.auth.me", () -> "ok");

        assertThat(result).isEqualTo("ok");

        assertThatThrownBy(() -> telemetry.observe("identity.auth.me", () -> {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        })).isInstanceOf(ResponseStatusException.class);

        RagDependencyOperationSnapshot snapshot = telemetry.snapshots().get(0);
        assertThat(snapshot.operation()).isEqualTo("identity.auth.me");
        assertThat(snapshot.successCount()).isEqualTo(1L);
        assertThat(snapshot.failureCount()).isEqualTo(1L);
        assertThat(snapshot.totalCount()).isEqualTo(2L);
        assertThat(snapshot.consecutiveFailureCount()).isEqualTo(1L);
        assertThat(snapshot.lastOutcome()).isEqualTo("failure");
        assertThat(snapshot.lastError()).isEqualTo("ResponseStatusException");
        assertThat(snapshot.lastErrorCategory()).isEqualTo("access_denied");
        assertThat(snapshot.lastObservedAt()).isNotNull();
        assertThat(snapshot.lastSuccessAt()).isNotNull();
        assertThat(snapshot.lastFailureAt()).isNotNull();
    }

    @Test
    void snapshotsCanBeFilteredByOperationPrefix() {
        telemetry.observe("identity.health", () -> "ok");
        telemetry.observe("storage.health", () -> "ok");
        telemetry.observe("identity.auth.me", () -> "ok");

        assertThat(telemetry.snapshots("identity."))
                .extracting(RagDependencyOperationSnapshot::operation)
                .containsExactly("identity.auth.me", "identity.health");
    }
}
