package com.alicia.cloudstorage.ragexecution.domain;

import java.time.Instant;
import java.util.UUID;

public record ExecutionLease(
        UUID executionId,
        String leaseOwner,
        Instant leaseUntil,
        long version
) {
    public ExecutionLease {
        if (executionId == null) {
            throw new IllegalArgumentException("executionId is required.");
        }
        if (leaseOwner == null || leaseOwner.isBlank()) {
            throw new IllegalArgumentException("leaseOwner is required.");
        }
        if (leaseUntil == null) {
            throw new IllegalArgumentException("leaseUntil is required.");
        }
    }
}
