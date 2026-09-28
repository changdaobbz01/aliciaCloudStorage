package com.alicia.cloudstorage.ragexecution.worker;

import com.alicia.cloudstorage.ragexecution.config.RagExecutionFeatureProperties;
import com.alicia.cloudstorage.ragexecution.config.RagExecutionLimitsProperties;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionLease;
import com.alicia.cloudstorage.ragexecution.port.ExecutionClock;
import com.alicia.cloudstorage.ragexecution.port.ExecutionLeaseStore;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ExecutionLeaseCoordinator {

    private final RagExecutionFeatureProperties features;
    private final RagExecutionLimitsProperties limits;
    private final ExecutionClock clock;
    private final ExecutionLeaseStore leaseStore;

    public ExecutionLeaseCoordinator(
            RagExecutionFeatureProperties features,
            RagExecutionLimitsProperties limits,
            ExecutionClock clock,
            ExecutionLeaseStore leaseStore
    ) {
        this.features = features;
        this.limits = limits;
        this.clock = clock;
        this.leaseStore = leaseStore;
    }

    public Optional<ExecutionLease> claimNext(String workerId) {
        if (!features.enabled() || !features.workerEnabled() || !features.cloudDispatchEnabled()) {
            return Optional.empty();
        }
        return leaseStore.claimNext(workerId, clock.now(), limits.leaseDuration());
    }
}
