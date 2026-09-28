package com.alicia.cloudstorage.ragexecution.port;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionLease;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface ExecutionLeaseStore {

    Optional<ExecutionLease> claimNext(String leaseOwner, Instant now, Duration leaseDuration);
}
