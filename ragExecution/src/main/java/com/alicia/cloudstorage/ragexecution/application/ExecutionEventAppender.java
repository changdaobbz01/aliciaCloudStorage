package com.alicia.cloudstorage.ragexecution.application;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionEventVisibility;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionEventJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionEventJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ExecutionEventAppender {

    private final ExecutionEventJpaRepository repository;

    public ExecutionEventAppender(ExecutionEventJpaRepository repository) {
        this.repository = repository;
    }

    public ExecutionEventJpaEntity appendPublic(
            UUID executionId,
            String eventType,
            JsonNode publicPayload,
            Instant now
    ) {
        long nextSequence = repository.findMaximumSequence(executionId.toString()) + 1;
        return repository.save(ExecutionEventJpaEntity.create(
                executionId,
                nextSequence,
                eventType,
                ExecutionEventVisibility.PUBLIC,
                publicPayload,
                now
        ));
    }
}
