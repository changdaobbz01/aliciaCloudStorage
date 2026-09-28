package com.alicia.cloudstorage.ragexecution.application;

import com.alicia.cloudstorage.ragexecution.api.internal.RegisterExecutionRequest;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionEventVisibility;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionEventJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionEventJpaRepository;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaRepository;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionStepJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionStepJpaRepository;
import com.alicia.cloudstorage.ragexecution.port.ExecutionIdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ExecutionRegistrationCreator {

    private final ExecutionJpaRepository executionRepository;
    private final ExecutionStepJpaRepository stepRepository;
    private final ExecutionEventJpaRepository eventRepository;
    private final ExecutionIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public ExecutionRegistrationCreator(
            ExecutionJpaRepository executionRepository,
            ExecutionStepJpaRepository stepRepository,
            ExecutionEventJpaRepository eventRepository,
            ExecutionIdGenerator idGenerator,
            ObjectMapper objectMapper
    ) {
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExecutionJpaEntity create(
            long ownerUserId,
            RegisterExecutionRequest request,
            ExecutionRegistrationValidator.ValidatedRegistration validated,
            Instant now
    ) {
        UUID executionId = idGenerator.nextExecutionId();
        ExecutionJpaEntity execution = ExecutionJpaEntity.createPending(
                executionId,
                ownerUserId,
                request.conversationId(),
                request.sourceResponseId(),
                request.planId(),
                request.planSchemaVersion(),
                request.planHash(),
                request.summary(),
                validated.risk(),
                "plan:" + request.planHash(),
                request.expiresAt().truncatedTo(ChronoUnit.MICROS),
                now
        );
        executionRepository.saveAndFlush(execution);

        List<ExecutionStepJpaEntity> steps = new ArrayList<>();
        for (int index = 0; index < validated.steps().size(); index++) {
            ExecutionRegistrationValidator.ValidatedStep step = validated.steps().get(index);
            steps.add(ExecutionStepJpaEntity.createPending(
                    idGenerator.nextStepId(),
                    executionId,
                    index,
                    step.stepKey(),
                    step.actionType(),
                    ExecutionRegistrationValidator.PAYLOAD_SCHEMA_VERSION,
                    step.payload(),
                    step.payloadHash(),
                    step.dependsOn(),
                    step.outputKey(),
                    step.requiredClientFields(),
                    now
            ));
        }
        stepRepository.saveAll(steps);

        ObjectNode publicEvent = objectMapper.createObjectNode();
        publicEvent.put("status", execution.getStatus().name());
        publicEvent.put("version", execution.getVersion());
        eventRepository.save(ExecutionEventJpaEntity.create(
                executionId,
                1,
                "EXECUTION_REGISTERED",
                ExecutionEventVisibility.PUBLIC,
                publicEvent,
                now
        ));
        return execution;
    }
}
