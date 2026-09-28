package com.alicia.cloudstorage.ragexecution.worker;

import com.alicia.cloudstorage.ragexecution.application.ExecutionEventAppender;
import com.alicia.cloudstorage.ragexecution.config.RagExecutionFeatureProperties;
import com.alicia.cloudstorage.ragexecution.config.RagExecutionLimitsProperties;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionStatus;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionStepStatus;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaRepository;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionStepJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionStepJpaRepository;
import com.alicia.cloudstorage.ragexecution.port.CloudActionGateway.CloudActionCommand;
import com.alicia.cloudstorage.ragexecution.port.CloudActionGateway.CloudActionResult;
import com.alicia.cloudstorage.ragexecution.port.ExecutionClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExecutionWorkStateService {

    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofSeconds(2),
            Duration.ofSeconds(10),
            Duration.ofSeconds(30)
    );

    private final RagExecutionFeatureProperties features;
    private final RagExecutionLimitsProperties limits;
    private final ExecutionJpaRepository executionRepository;
    private final ExecutionStepJpaRepository stepRepository;
    private final ExecutionEventAppender eventAppender;
    private final ExecutionClock clock;
    private final ObjectMapper objectMapper;

    public ExecutionWorkStateService(
            RagExecutionFeatureProperties features,
            RagExecutionLimitsProperties limits,
            ExecutionJpaRepository executionRepository,
            ExecutionStepJpaRepository stepRepository,
            ExecutionEventAppender eventAppender,
            ExecutionClock clock,
            ObjectMapper objectMapper
    ) {
        this.features = features;
        this.limits = limits;
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
        this.eventAppender = eventAppender;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Optional<CloudActionCommand> prepare(UUID executionId, String workerId) {
        Instant now = clock.now();
        ExecutionJpaEntity execution = locked(executionId);
        requireLease(execution, workerId, now);
        execution.renewLease(workerId, now, now.plus(limits.leaseDuration()));
        List<ExecutionStepJpaEntity> steps = steps(executionId);
        if (steps.isEmpty()) {
            failWithoutDispatch(execution, "execution_steps_missing", now);
            return Optional.empty();
        }
        if (steps.stream().anyMatch(step -> !features.allowedActions().contains(step.getActionType()))) {
            failWithoutDispatch(execution, "action_not_enabled", now);
            return Optional.empty();
        }
        if (steps.stream().anyMatch(step -> step.getActionType() == ExecutionActionType.SHARE_CREATE
                && step.getPayload().path("nodeIds").size() != 1)) {
            failWithoutDispatch(execution, "phase4_single_object_required", now);
            return Optional.empty();
        }
        Optional<ExecutionStepJpaEntity> next = steps.stream()
                .filter(step -> step.getStatus() != ExecutionStepStatus.SUCCEEDED
                        && step.getStatus() != ExecutionStepStatus.SKIPPED)
                .findFirst();
        if (next.isEmpty()) {
            completeFromStoredSteps(execution, steps, now);
            return Optional.empty();
        }
        ExecutionStepJpaEntity step = next.get();
        if (step.getStatus() == ExecutionStepStatus.FAILED) {
            failOrPartiallyComplete(execution, steps, "step_failed", now);
            return Optional.empty();
        }
        requireDependenciesSucceeded(step, steps);
        if (step.getActionType().location() == ExecutionActionType.ExecutionLocation.CLIENT) {
            prepareClientInput(execution, step, steps, now);
            return Optional.empty();
        }
        if (step.getStatus() == ExecutionStepStatus.RUNNING) {
            step.recoverRunningAttempt(now);
            append(execution, "EXECUTION_RECOVERED", step, "RUNNING", now);
        } else {
            step.start(now);
            append(execution, "EXECUTION_STARTED", step, "RUNNING", now);
        }
        executionRepository.flush();
        stepRepository.flush();
        return Optional.of(new CloudActionCommand(
                execution.getId(),
                step.getId(),
                execution.getOwnerUserId(),
                step.getActionType(),
                step.getPayloadSchemaVersion(),
                step.getPayload()
        ));
    }

    @Transactional
    public void succeed(UUID executionId, UUID stepId, String workerId, CloudActionResult result) {
        Instant now = clock.now();
        ExecutionJpaEntity execution = locked(executionId);
        requireLease(execution, workerId, now);
        ExecutionStepJpaEntity step = stepRepository.findByIdAndExecutionId(
                        stepId.toString(), executionId.toString())
                .orElseThrow(() -> new IllegalStateException("Execution step is missing."));
        if (step.getStatus() == ExecutionStepStatus.SUCCEEDED) {
            finishOrQueue(execution, steps(executionId), result.resultCode(), now);
            return;
        }
        step.succeed(result.result(), now);
        stepRepository.flush();
        append(execution, "STEP_SUCCEEDED", step, "SUCCEEDED", now);
        finishOrQueue(execution, steps(executionId), result.resultCode(), now);
    }

    @Transactional
    public void fail(
            UUID executionId,
            UUID stepId,
            String workerId,
            String errorCode,
            boolean retryable
    ) {
        Instant now = clock.now();
        ExecutionJpaEntity execution = locked(executionId);
        requireLease(execution, workerId, now);
        ExecutionStepJpaEntity step = stepRepository.findByIdAndExecutionId(
                        stepId.toString(), executionId.toString())
                .orElseThrow(() -> new IllegalStateException("Execution step is missing."));
        if (step.getStatus() == ExecutionStepStatus.SUCCEEDED) {
            finishOrQueue(execution, steps(executionId), "RECOVERED_CLOUD_RECEIPT", now);
            return;
        }
        if (step.getStatus() != ExecutionStepStatus.RUNNING) {
            throw new IllegalStateException("Only a running step can record a dispatch failure.");
        }

        Duration delay = retryDelay(step.getAttempts());
        Instant availableAt = now.plus(delay);
        if (retryable
                && step.getAttempts() < limits.maxAttempts()
                && execution.getExpiresAt().isAfter(availableAt)) {
            step.retry(errorCode, "Cloud action will be retried.", availableAt, now);
            execution.waitForRetry(errorCode, "Cloud action will be retried.", now);
            stepRepository.flush();
            executionRepository.flush();
            append(execution, "EXECUTION_RETRY_SCHEDULED", step, "RETRY_WAIT", now);
            return;
        }

        step.fail(errorCode, "Cloud action could not be completed.", now);
        stepRepository.flush();
        append(execution, "STEP_FAILED", step, "FAILED", now);
        failOrPartiallyComplete(execution, steps(executionId), errorCode, now);
    }

    @Transactional
    public boolean requeueOneReadyRetry() {
        Instant now = clock.now();
        return executionRepository.lockNextRetryReady(now).map(execution -> {
            execution.transitionTo(ExecutionStatus.QUEUED, now);
            executionRepository.flush();
            append(execution, "EXECUTION_REQUEUED", null, "QUEUED", now);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean expireOne() {
        Instant now = clock.now();
        return executionRepository.lockNextExpired(now).map(execution -> {
            if (execution.getStatus() == ExecutionStatus.WAITING_CLIENT_INPUT) {
                steps(execution.getId()).stream()
                        .filter(step -> step.getStatus() == ExecutionStepStatus.WAITING_CLIENT_INPUT)
                        .forEach(step -> step.failClientInput(
                                "client_input_timeout",
                                "Client input was not provided before the deadline.",
                                now
                        ));
                stepRepository.flush();
            }
            execution.transitionTo(ExecutionStatus.EXPIRED, now);
            executionRepository.flush();
            append(execution, "EXECUTION_EXPIRED", null, "EXPIRED", now);
            return true;
        }).orElse(false);
    }

    private ExecutionJpaEntity locked(UUID executionId) {
        return executionRepository.lockById(executionId.toString())
                .orElseThrow(() -> new IllegalStateException("Execution is missing."));
    }

    private List<ExecutionStepJpaEntity> steps(UUID executionId) {
        return stepRepository.findByExecutionIdOrderByStepIndexAsc(executionId.toString());
    }

    private void requireLease(ExecutionJpaEntity execution, String workerId, Instant now) {
        if (execution.getStatus() != ExecutionStatus.RUNNING
                || !workerId.equals(execution.getLeaseOwner())
                || execution.getLeaseUntil() == null
                || execution.getLeaseUntil().isBefore(now)) {
            throw new IllegalStateException("Worker does not own an active execution lease.");
        }
    }

    private void completeFromStoredSteps(
            ExecutionJpaEntity execution,
            List<ExecutionStepJpaEntity> steps,
            Instant now
    ) {
        if (execution.getStatus() != ExecutionStatus.RUNNING) {
            return;
        }
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("completedSteps", steps.size());
        summary.put("totalSteps", steps.size());
        execution.complete("RECOVERED_CLOUD_RECEIPT", summary, now);
        executionRepository.flush();
        append(execution, "EXECUTION_SUCCEEDED", steps.isEmpty() ? null : steps.getLast(), "SUCCEEDED", now);
    }

    private void finishOrQueue(
            ExecutionJpaEntity execution,
            List<ExecutionStepJpaEntity> steps,
            String lastResultCode,
            Instant now
    ) {
        long completed = steps.stream().filter(step -> step.getStatus() == ExecutionStepStatus.SUCCEEDED).count();
        boolean hasRemaining = steps.stream().anyMatch(step -> step.getStatus() == ExecutionStepStatus.PENDING
                || step.getStatus() == ExecutionStepStatus.RETRY_WAIT
                || step.getStatus() == ExecutionStepStatus.RUNNING);
        if (hasRemaining) {
            execution.queueNextStep(now, now.plus(limits.maxQueueWait()));
            executionRepository.flush();
            append(execution, "EXECUTION_STEP_QUEUED", null, "QUEUED", now);
            return;
        }
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("completedSteps", completed);
        summary.put("totalSteps", steps.size());
        summary.put("resultCode", lastResultCode);
        execution.complete(steps.size() == 1 ? lastResultCode : "WORKFLOW_SUCCEEDED", summary, now);
        executionRepository.flush();
        append(execution, "EXECUTION_SUCCEEDED", steps.isEmpty() ? null : steps.getLast(), "SUCCEEDED", now);
    }

    private void failOrPartiallyComplete(
            ExecutionJpaEntity execution,
            List<ExecutionStepJpaEntity> steps,
            String errorCode,
            Instant now
    ) {
        long completed = steps.stream().filter(step -> step.getStatus() == ExecutionStepStatus.SUCCEEDED).count();
        if (completed > 0) {
            ObjectNode summary = objectMapper.createObjectNode();
            summary.put("completedSteps", completed);
            summary.put("totalSteps", steps.size());
            summary.put("errorCode", errorCode);
            execution.completePartially("WORKFLOW_PARTIALLY_COMPLETED", summary, errorCode, now);
            executionRepository.flush();
            append(execution, "EXECUTION_PARTIALLY_SUCCEEDED", null, "PARTIALLY_SUCCEEDED", now);
            return;
        }
        execution.fail(errorCode, "Cloud action could not be completed.", now);
        executionRepository.flush();
        append(execution, "EXECUTION_FAILED", null, "FAILED", now);
    }

    private void requireDependenciesSucceeded(
            ExecutionStepJpaEntity step,
            List<ExecutionStepJpaEntity> steps
    ) {
        java.util.Map<String, ExecutionStepJpaEntity> byKey = steps.stream()
                .collect(java.util.stream.Collectors.toMap(ExecutionStepJpaEntity::getStepKey, value -> value));
        for (String dependency : step.getDependsOn()) {
            ExecutionStepJpaEntity source = byKey.get(dependency);
            if (source == null || source.getStatus() != ExecutionStepStatus.SUCCEEDED) {
                throw new IllegalStateException("Execution step dependency is not satisfied.");
            }
        }
    }

    private void prepareClientInput(
            ExecutionJpaEntity execution,
            ExecutionStepJpaEntity step,
            List<ExecutionStepJpaEntity> steps,
            Instant now
    ) {
        if (step.getActionType() != ExecutionActionType.UPLOAD_FILES
                || step.getStatus() != ExecutionStepStatus.PENDING) {
            throw new IllegalStateException("Unsupported client-input workflow state.");
        }
        JsonNodeReference reference = parentReference(step);
        ExecutionStepJpaEntity source = steps.stream()
                .filter(candidate -> candidate.getStepKey().equals(reference.stepKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Client-input source step is missing."));
        long parentId = source.getResult() == null ? 0L : source.getResult().path(reference.outputField()).asLong(0L);
        if (source.getActionType() != ExecutionActionType.FOLDER_CREATE
                || source.getStatus() != ExecutionStepStatus.SUCCEEDED
                || !"nodeId".equals(reference.outputField())
                || parentId <= 0) {
            throw new IllegalStateException("Client-input output reference could not be resolved safely.");
        }
        Instant deadline = now.plus(limits.clientInputTtl());
        step.start(now);
        ObjectNode request = objectMapper.createObjectNode();
        request.put("inputType", "UPLOAD_FILES");
        request.put("parentId", parentId);
        request.put("deadline", deadline.toString());
        var fields = request.putArray("requiredFields");
        step.getRequiredClientFields().forEach(fields::add);
        step.waitForClientInput(request, now);
        execution.waitForClientInput(now, deadline);
        stepRepository.flush();
        executionRepository.flush();
        append(execution, "STEP_WAITING_CLIENT_INPUT", step, "WAITING_CLIENT_INPUT", now);
        append(execution, "EXECUTION_WAITING_CLIENT_INPUT", step, "WAITING_CLIENT_INPUT", now);
    }

    private JsonNodeReference parentReference(ExecutionStepJpaEntity step) {
        var reference = step.getPayload().path("parentIdReference");
        String stepKey = reference.path("stepKey").asText("");
        String outputField = reference.path("outputField").asText("");
        if (stepKey.isBlank() || outputField.isBlank()) {
            throw new IllegalStateException("Client-input output reference is invalid.");
        }
        return new JsonNodeReference(stepKey, outputField);
    }

    private record JsonNodeReference(String stepKey, String outputField) {
    }

    private void failWithoutDispatch(ExecutionJpaEntity execution, String errorCode, Instant now) {
        execution.fail(errorCode, "Execution is not enabled by the current rollout policy.", now);
        executionRepository.flush();
        append(execution, "EXECUTION_FAILED", null, "FAILED", now);
    }

    private Duration retryDelay(int attempts) {
        int index = Math.max(0, Math.min(attempts - 1, RETRY_DELAYS.size() - 1));
        return RETRY_DELAYS.get(index);
    }

    private void append(
            ExecutionJpaEntity execution,
            String eventType,
            ExecutionStepJpaEntity step,
            String status,
            Instant now
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status", status);
        payload.put("version", execution.getVersion());
        if (step != null) {
            payload.put("stepId", step.getId().toString());
            payload.put("actionType", step.getActionType().name());
            payload.put("attempts", step.getAttempts());
        }
        eventAppender.appendPublic(execution.getId(), eventType, payload, now);
    }
}
