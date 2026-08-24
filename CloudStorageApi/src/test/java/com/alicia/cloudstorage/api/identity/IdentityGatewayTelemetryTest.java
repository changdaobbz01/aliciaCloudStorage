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
        assertThat(snapshot.lastOutcome()).isEqualTo("failure");
        assertThat(snapshot.lastError()).isEqualTo("PrincipalAccessException");
        assertThat(snapshot.lastDurationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(snapshot.lastObservedAt()).isNotNull();
    }

    @Test
    void snapshotsAreSortedByOperationName() {
        telemetry.observe("internal.getUser", () -> "ok");
        telemetry.observe("auth.me", () -> "ok");

        assertThat(telemetry.snapshots())
                .extracting(IdentityGatewayOperationSnapshot::operation)
                .containsExactly("auth.me", "internal.getUser");
    }
}
