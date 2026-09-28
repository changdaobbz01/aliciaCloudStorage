package com.alicia.cloudstorage.ragexecution.infrastructure.persistence;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionLease;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionStatus;
import com.alicia.cloudstorage.ragexecution.port.ExecutionLeaseStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
public class ExecutionLeasePersistenceAdapter implements ExecutionLeaseStore {

    private final ExecutionJpaRepository repository;

    public ExecutionLeasePersistenceAdapter(ExecutionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Optional<ExecutionLease> claimNext(String leaseOwner, Instant now, Duration leaseDuration) {
        String normalizedOwner = requireLeaseOwner(leaseOwner);
        if (now == null) {
            throw new IllegalArgumentException("now is required.");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive.");
        }

        return repository.lockNextClaimable(now).map(execution -> {
            if (execution.getStatus() == ExecutionStatus.RUNNING) {
                execution.reclaimLease(normalizedOwner, now, now.plus(leaseDuration));
            } else {
                execution.claimLease(normalizedOwner, now, now.plus(leaseDuration));
            }
            repository.flush();
            return new ExecutionLease(
                    execution.getId(),
                    execution.getLeaseOwner(),
                    execution.getLeaseUntil(),
                    execution.getVersion()
            );
        });
    }

    private String requireLeaseOwner(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("leaseOwner is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("leaseOwner must not exceed 128 characters.");
        }
        return normalized;
    }
}
