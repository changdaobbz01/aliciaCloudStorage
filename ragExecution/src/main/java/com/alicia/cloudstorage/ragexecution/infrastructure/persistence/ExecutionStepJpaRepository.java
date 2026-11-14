package com.alicia.cloudstorage.ragexecution.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExecutionStepJpaRepository extends JpaRepository<ExecutionStepJpaEntity, String> {

    List<ExecutionStepJpaEntity> findByExecutionIdOrderByStepIndexAsc(String executionId);

    List<ExecutionStepJpaEntity> findByExecutionIdIn(Collection<String> executionIds);

    Optional<ExecutionStepJpaEntity> findByIdAndExecutionId(String id, String executionId);

    long countByAttemptsGreaterThan(int attempts);
}
