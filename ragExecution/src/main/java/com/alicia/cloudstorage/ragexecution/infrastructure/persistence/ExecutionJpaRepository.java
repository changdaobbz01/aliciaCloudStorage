package com.alicia.cloudstorage.ragexecution.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Optional;

public interface ExecutionJpaRepository extends JpaRepository<ExecutionJpaEntity, String> {

    Optional<ExecutionJpaEntity> findByOwnerUserIdAndIdempotencyKey(Long ownerUserId, String idempotencyKey);

    Optional<ExecutionJpaEntity> findByOwnerUserIdAndPlanId(Long ownerUserId, String planId);

    Optional<ExecutionJpaEntity> findByIdAndOwnerUserId(String id, Long ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from ExecutionJpaEntity execution where execution.id = :id and execution.ownerUserId = :ownerUserId")
    Optional<ExecutionJpaEntity> lockByIdAndOwnerUserId(
            @Param("id") String id,
            @Param("ownerUserId") Long ownerUserId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from ExecutionJpaEntity execution where execution.id = :id")
    Optional<ExecutionJpaEntity> lockById(@Param("id") String id);

    @Query(value = """
            SELECT *
            FROM rag_execution
            WHERE expires_at > :now
              AND (status = 'QUEUED'
                   OR (status = 'RUNNING' AND lease_until < :now))
            ORDER BY queued_at ASC, created_at ASC, id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<ExecutionJpaEntity> lockNextClaimable(@Param("now") Instant now);

    @Query(value = """
            SELECT execution.*
            FROM rag_execution execution
            WHERE execution.status = 'RETRY_WAIT'
              AND execution.expires_at > :now
              AND EXISTS (
                  SELECT 1 FROM rag_execution_step step
                  WHERE step.execution_id = execution.id
                    AND step.status = 'RETRY_WAIT'
                    AND step.available_at <= :now
              )
            ORDER BY execution.updated_at ASC, execution.id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<ExecutionJpaEntity> lockNextRetryReady(@Param("now") Instant now);

    @Query(value = """
            SELECT *
            FROM rag_execution
            WHERE expires_at <= :now
              AND (status IN ('PENDING_CONFIRMATION', 'QUEUED', 'RETRY_WAIT', 'WAITING_CLIENT_INPUT')
                   OR (status = 'RUNNING' AND lease_until < :now))
            ORDER BY expires_at ASC, id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<ExecutionJpaEntity> lockNextExpired(@Param("now") Instant now);
}
