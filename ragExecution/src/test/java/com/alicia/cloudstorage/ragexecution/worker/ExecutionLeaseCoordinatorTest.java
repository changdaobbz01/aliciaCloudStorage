package com.alicia.cloudstorage.ragexecution.worker;

import com.alicia.cloudstorage.ragexecution.config.RagExecutionFeatureProperties;
import com.alicia.cloudstorage.ragexecution.config.RagExecutionLimitsProperties;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionLease;
import com.alicia.cloudstorage.ragexecution.port.ExecutionLeaseStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionLeaseCoordinatorTest {

    @Test
    void doesNotTouchTheTaskPoolWhileWorkerFeatureIsDisabled() {
        AtomicBoolean called = new AtomicBoolean(false);
        ExecutionLeaseStore store = (owner, now, duration) -> {
            called.set(true);
            return Optional.empty();
        };
        ExecutionLeaseCoordinator coordinator = new ExecutionLeaseCoordinator(
                new RagExecutionFeatureProperties(false, false, false, false, false, true, Set.of()),
                limits(),
                () -> Instant.parse("2026-09-18T04:00:00Z"),
                store
        );

        Optional<ExecutionLease> result = coordinator.claimNext("worker-1");

        assertThat(result).isEmpty();
        assertThat(called).isFalse();
    }

    private RagExecutionLimitsProperties limits() {
        return new RagExecutionLimitsProperties(
                10,
                100,
                65_536,
                Duration.ofMinutes(10),
                Duration.ofMinutes(2),
                Duration.ofMinutes(15),
                Duration.ofSeconds(30),
                3
        );
    }
}
