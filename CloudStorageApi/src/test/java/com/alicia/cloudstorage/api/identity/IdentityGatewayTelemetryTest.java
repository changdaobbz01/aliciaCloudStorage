package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.principal.PrincipalAccessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityGatewayTelemetryTest {

    private final IdentityGatewayTelemetry telemetry = new IdentityGatewayTelemetry();

    @Test
    void observeRecordsSuccessAndFailureWithoutChangingResult() {
        String result = telemetry.observe("auth.me", () -> "ok");

        assertThat(result).isEqualTo("ok");

        assertThatThrownBy(() -> telemetry.observe("auth.me", () -> {
            throw new PrincipalAccessException("stale");
        })).isInstanceOf(PrincipalAccessException.class);

        IdentityGatewayOperationSnapshot snapshot = telemetry.snapshots().get(0);
        assertThat(snapshot.operation()).isEqualTo("auth.me");
        assertThat(snapshot.successCount()).isEqualTo(1L);
        assertThat(snapshot.failureCount()).isEqualTo(1L);
        assertThat(snapshot.totalCount()).isEqualTo(2L);
        assertThat(snapshot.consecutiveFailureCount()).isEqualTo(1L);
        assertThat(snapshot.lastOutcome()).isEqualTo("failure");
        assertThat(snapshot.lastError()).isEqualTo("PrincipalAccessException");
        assertThat(snapshot.lastErrorCategory()).isEqualTo("access_denied");
        assertThat(snapshot.lastDurationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(snapshot.averageDurationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(snapshot.maxDurationMs()).isGreaterThanOrEqualTo(snapshot.lastDurationMs());
        assertThat(snapshot.lastObservedAt()).isNotNull();
        assertThat(snapshot.lastSuccessAt()).isNotNull();
        assertThat(snapshot.lastFailureAt()).isNotNull();
    }

    @Test
    void snapshotsAreSortedByOperationName() {
        telemetry.observe("internal.getUser", () -> "ok");
        telemetry.observe("auth.me", () -> "ok");

        assertThat(telemetry.snapshots())
                .extracting(IdentityGatewayOperationSnapshot::operation)
                .containsExactly("auth.me", "internal.getUser");
    }

    @Test
    void successfulObservationClearsConsecutiveFailureCount() {
        assertThatThrownBy(() -> telemetry.observe("jwks.fetch", () -> {
            throw new IdentityServiceUnavailableException("jwks unavailable");
        })).isInstanceOf(IdentityServiceUnavailableException.class);

        telemetry.observe("jwks.fetch", () -> "ok");

        IdentityGatewayOperationSnapshot snapshot = telemetry.snapshots().get(0);
        assertThat(snapshot.operation()).isEqualTo("jwks.fetch");
        assertThat(snapshot.successCount()).isEqualTo(1L);
        assertThat(snapshot.failureCount()).isEqualTo(1L);
        assertThat(snapshot.consecutiveFailureCount()).isZero();
        assertThat(snapshot.lastOutcome()).isEqualTo("success");
        assertThat(snapshot.lastError()).isNull();
        assertThat(snapshot.lastErrorCategory()).isNull();
        assertThat(snapshot.lastFailureAt()).isNotNull();
    }
}
