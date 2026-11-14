package com.alicia.cloudstorage.ragexecution.application;

import com.alicia.cloudstorage.ragexecution.config.RagExecutionFeatureProperties;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionEventVisibility;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionStatus;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionEventJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionEventJpaRepository;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaRepository;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionStepJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionStepJpaRepository;
import com.alicia.cloudstorage.ragexecution.port.ExecutionClock;
import com.alicia.cloudstorage.ragexecution.port.IdentityAccessVerifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminExecutionQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final List<ExecutionStatus> ACTIVE_STATUSES = List.of(
            ExecutionStatus.PENDING_CONFIRMATION,
            ExecutionStatus.QUEUED,
            ExecutionStatus.RUNNING,
            ExecutionStatus.RETRY_WAIT,
            ExecutionStatus.WAITING_CLIENT_INPUT
    );
    private static final List<ExecutionStatus> ATTENTION_STATUSES = List.of(
            ExecutionStatus.FAILED,
            ExecutionStatus.PARTIALLY_SUCCEEDED
    );

    private final RagExecutionFeatureProperties features;
    private final IdentityAccessVerifier identityVerifier;
    private final ExecutionJpaRepository executionRepository;
    private final ExecutionStepJpaRepository stepRepository;
    private final ExecutionEventJpaRepository eventRepository;
    private final ExecutionClock clock;

    public AdminExecutionQueryService(
            RagExecutionFeatureProperties features,
            IdentityAccessVerifier identityVerifier,
            ExecutionJpaRepository executionRepository,
            ExecutionStepJpaRepository stepRepository,
            ExecutionEventJpaRepository eventRepository,
            ExecutionClock clock
    ) {
        this.features = features;
        this.identityVerifier = identityVerifier;
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Overview overview(String authorization) {
        requireAdmin(authorization);
        EnumMap<ExecutionStatus, Long> counts = new EnumMap<>(ExecutionStatus.class);
        for (ExecutionStatus status : ExecutionStatus.values()) {
            counts.put(status, executionRepository.countByStatus(status));
        }
        LinkedHashMap<String, Long> publicCounts = new LinkedHashMap<>();
        counts.forEach((status, count) -> publicCounts.put(status.name(), count));
        return new Overview(
                executionRepository.count(),
                executionRepository.countByStatusIn(ACTIVE_STATUSES),
                executionRepository.countByStatusIn(ATTENTION_STATUSES),
                counts.get(ExecutionStatus.RETRY_WAIT),
                stepRepository.countByAttemptsGreaterThan(1),
                Map.copyOf(publicCounts),
                clock.now()
        );
    }

    @Transactional(readOnly = true)
    public PageResult list(
            String rawStatus,
            Long ownerUserId,
            int page,
            int size,
            String authorization
    ) {
        requireAdmin(authorization);
        ExecutionStatus status = parseStatus(rawStatus);
        if (ownerUserId != null && ownerUserId <= 0) {
            throw new ExecutionAccessException(400, "invalid_owner_user_id");
        }
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ExecutionAccessException(400, "invalid_page_request");
        }
        PageRequest request = PageRequest.of(
                page - 1,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<ExecutionJpaEntity> result = findPage(status, ownerUserId, request);
        Map<String, List<ExecutionStepJpaEntity>> stepsByExecution = stepsByExecution(result.getContent());
        List<Summary> items = result.getContent().stream()
                .map(execution -> summary(execution, stepsByExecution.getOrDefault(execution.getId().toString(), List.of())))
                .toList();
        return new PageResult(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Detail get(String executionId, String authorization) {
        requireAdmin(authorization);
        String id = requireUuid(executionId);
        ExecutionJpaEntity execution = executionRepository.findById(id)
                .orElseThrow(() -> new ExecutionAccessException(404, "execution_not_found"));
        List<ExecutionStepJpaEntity> steps = stepRepository.findByExecutionIdOrderByStepIndexAsc(id);
        List<Event> events = eventRepository
                .findByExecutionIdAndVisibilityAndSequenceNoGreaterThanOrderBySequenceNoAsc(
                        id,
                        ExecutionEventVisibility.PUBLIC,
                        0
                )
                .stream()
                .map(this::event)
                .toList();
        return new Detail(summary(execution, steps), steps.stream().map(this::step).toList(), events);
    }

    private void requireAdmin(String authorization) {
        if (!features.enabled()) {
            throw new ExecutionAccessException(404, "execution_api_disabled");
        }
        IdentityAccessVerifier.IdentityPrincipal principal = identityVerifier.requireRagAccess(authorization);
        if (!"RAG_ADMIN".equals(principal.ragRole())) {
            throw new ExecutionAccessException(403, "rag_admin_observability_required");
        }
    }

    private ExecutionStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            return ExecutionStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ExecutionAccessException(400, "invalid_execution_status");
        }
    }

    private String requireUuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new ExecutionAccessException(400, "invalid_execution_id");
        }
    }

    private Page<ExecutionJpaEntity> findPage(
            ExecutionStatus status,
            Long ownerUserId,
            PageRequest request
    ) {
        if (status != null && ownerUserId != null) {
            return executionRepository.findByStatusAndOwnerUserId(status, ownerUserId, request);
        }
        if (status != null) {
            return executionRepository.findByStatus(status, request);
        }
        if (ownerUserId != null) {
            return executionRepository.findByOwnerUserId(ownerUserId, request);
        }
        return executionRepository.findAll(request);
    }

    private Map<String, List<ExecutionStepJpaEntity>> stepsByExecution(Collection<ExecutionJpaEntity> executions) {
        List<String> ids = executions.stream().map(execution -> execution.getId().toString()).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return stepRepository.findByExecutionIdIn(ids).stream()
                .sorted(java.util.Comparator.comparingInt(ExecutionStepJpaEntity::getStepIndex))
                .collect(Collectors.groupingBy(
                        step -> step.getExecutionId().toString(),
                        LinkedHashMap::new,
                        Collectors.toCollection(ArrayList::new)
                ));
    }

    private Summary summary(ExecutionJpaEntity execution, List<ExecutionStepJpaEntity> steps) {
        int totalAttempts = steps.stream().mapToInt(ExecutionStepJpaEntity::getAttempts).sum();
        int maximumAttempts = steps.stream().mapToInt(ExecutionStepJpaEntity::getAttempts).max().orElse(0);
        List<String> actions = steps.stream().map(step -> step.getActionType().name()).distinct().toList();
        return new Summary(
                execution.getId().toString(),
                execution.getOwnerUserId(),
                execution.getStatus().name(),
                execution.getRisk().name(),
                execution.getVersion(),
                steps.size(),
                totalAttempts,
                maximumAttempts,
                actions,
                execution.getExpiresAt(),
                execution.getConfirmedAt(),
                execution.getQueuedAt(),
                execution.getStartedAt(),
                execution.getFinishedAt(),
                execution.getResultCode(),
                execution.getErrorCode(),
                execution.getCreatedAt(),
                execution.getUpdatedAt()
        );
    }

    private Step step(ExecutionStepJpaEntity step) {
        return new Step(
                step.getId().toString(),
                step.getStepIndex(),
                step.getActionType().name(),
                step.getStatus().name(),
                step.getAttempts(),
                step.getAvailableAt(),
                step.getStartedAt(),
                step.getFinishedAt(),
                step.getErrorCode()
        );
    }

    private Event event(ExecutionEventJpaEntity event) {
        return new Event(
                event.getSequenceNo(),
                event.getEventType(),
                event.getCreatedAt()
        );
    }

    public record Overview(
            long totalExecutions,
            long activeExecutions,
            long attentionExecutions,
            long retryWaitExecutions,
            long retriedSteps,
            Map<String, Long> statusCounts,
            Instant generatedAt
    ) {
    }

    public record PageResult(
            List<Summary> items,
            int page,
            int size,
            long totalItems,
            int totalPages
    ) {
    }

    public record Summary(
            String executionId,
            long ownerUserId,
            String status,
            String risk,
            long version,
            int stepCount,
            int totalAttempts,
            int maximumAttempts,
            List<String> actionTypes,
            Instant expiresAt,
            Instant confirmedAt,
            Instant queuedAt,
            Instant startedAt,
            Instant finishedAt,
            String resultCode,
            String errorCode,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record Detail(Summary execution, List<Step> steps, List<Event> events) {
    }

    public record Step(
            String stepId,
            int stepIndex,
            String actionType,
            String status,
            int attempts,
            Instant availableAt,
            Instant startedAt,
            Instant finishedAt,
            String errorCode
    ) {
    }

    public record Event(
            long sequence,
            String type,
            Instant createdAt
    ) {
    }
}
