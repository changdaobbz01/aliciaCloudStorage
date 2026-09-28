package com.alicia.cloudstorage.ragexecution.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExecutionEventJpaRepository extends JpaRepository<ExecutionEventJpaEntity, Long> {

    List<ExecutionEventJpaEntity> findByExecutionIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(
            String executionId,
            long sequenceNo
    );

    @Query("select coalesce(max(event.sequenceNo), 0) from ExecutionEventJpaEntity event where event.executionId = :executionId")
    long findMaximumSequence(@Param("executionId") String executionId);
}
