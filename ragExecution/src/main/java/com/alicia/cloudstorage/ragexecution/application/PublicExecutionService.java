package com.alicia.cloudstorage.ragexecution.application;

import com.alicia.cloudstorage.ragexecution.api.publicapi.ExecutionEventResponse;
import com.alicia.cloudstorage.ragexecution.api.publicapi.ExecutionResponse;
import com.alicia.cloudstorage.ragexecution.api.publicapi.ExecutionStepResponse;
import com.alicia.cloudstorage.ragexecution.api.publicapi.CompleteClientInputRequest;
import com.alicia.cloudstorage.ragexecution.config.RagExecutionFeatureProperties;
import com.alicia.cloudstorage.ragexecution.config.RagExecutionLimitsProperties;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionStatus;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionEventJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionEventJpaRepository;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaRepository;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionStepJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionStepJpaRepository;
import com.alicia.cloudstorage.ragexecution.port.ExecutionClock;
import com.alicia.cloudstorage.ragexecution.port.CloudActionGateway;
import com.alicia.cloudstorage.ragexecution.port.IdentityAccessVerifier;
import com.alicia.cloudstorage.ragexecution.infrastructure.cloud.CloudActionDispatchException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import java.util.Set;

@Service
public class PublicExecutionService {

    private final RagExecutionFeatureProperties features;
    private final RagExecutionLimitsProperties limits;
    private final IdentityAccessVerifier identityVerifier;
    private final ExecutionJpaRepository executionRepository;
    private final ExecutionStepJpaRepository stepRepository;
    private final ExecutionEventJpaRepository eventRepository;
    private final ExecutionEventAppender eventAppender;
    private final ExecutionClock clock;
    private final ObjectMapper objectMapper;
    private final CloudActionGateway cloudActionGateway;

    public PublicExecutionService(
            RagExecutionFeatureProperties features,
            RagExecutionLimitsProperties limits,
            IdentityAccessVerifier identityVerifier,
            ExecutionJpaRepository executionRepository,
            ExecutionStepJpaRepository stepRepository,
            ExecutionEventJpaRepository eventRepository,
            ExecutionEventAppender eventAppender,
            ExecutionClock clock,
            ObjectMapper objectMapper,
            CloudActionGateway cloudActionGateway
    ) {
        this.features = features;
        this.limits = limits;
        this.identityVerifier = identityVerifier;
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
        this.eventAppender = eventAppender;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.cloudActionGateway = cloudActionGateway;
    }

    @Transactional(readOnly = true)
    public ExecutionResponse get(String executionId, String authorization) {
        requirePublicApi();
        IdentityAccessVerifier.IdentityPrincipal principal = identityVerifier.requireRagAccess(authorization);
        ExecutionJpaEntity execution = owned(executionId, principal.userId());
        return response(execution, steps(execution));
    }

    @Transactional
    public ExecutionResponse confirm(String executionId, Long expectedVersion, String authorization) {
        requireConfirmation();
        if (expectedVersion == null || expectedVersion < 0) {
            throw new ExecutionAccessException(400, "expected_version_required");
        }
        IdentityAccessVerifier.IdentityPrincipal principal = identityVerifier.requireRagAccess(authorization);
        if (features.adminOnly() && !"RAG_ADMIN".equals(principal.ragRole())) {
            throw new ExecutionAccessException(403, "rag_admin_confirmation_required");
        }
        ExecutionJpaEntity execution = lockedOwned(executionId, principal.userId());
        List<ExecutionStepJpaEntity> steps = steps(execution);

        if (execution.getStatus() != ExecutionStatus.PENDING_CONFIRMATION) {
            if (isPreviouslyConfirmed(execution.getStatus()) && expectedVersion <= execution.getVersion()) {
                return response(execution, steps);
            }
            throw new ExecutionAccessException(409, "execution_not_confirmable");
        }
        if (execution.getVersion() != expectedVersion) {
            throw new ExecutionAccessException(409, "execution_version_conflict");
        }
        Instant now = clock.now();
        if (!execution.getExpiresAt().isAfter(now)) {
            throw new ExecutionAccessException(409, "execution_confirmation_expired");
        }
        if (steps.isEmpty() || steps.stream().anyMatch(step -> !features.allowedActions().contains(step.getActionType()))) {
            throw new ExecutionAccessException(422, "action_not_enabled");
        }
        if (steps.stream().anyMatch(step -> step.getActionType() == ExecutionActionType.SHARE_CREATE
                && step.getPayload().path("nodeIds").size() != 1)) {
            throw new ExecutionAccessException(422, "phase4_single_object_required");
        }

        execution.confirmAndQueue(now, now.plus(limits.maxQueueWait()));
        executionRepository.saveAndFlush(execution);
        ObjectNode payload = statusPayload(execution);
        payload.put("actionType", steps.getFirst().getActionType().name());
        payload.put("stepCount", steps.size());
        eventAppender.appendPublic(execution.getId(), "EXECUTION_CONFIRMED", payload, now);
        return response(execution, steps);
    }

    @Transactional
    public ExecutionResponse completeClientInput(
            String executionId,
            CompleteClientInputRequest request,
            String authorization
    ) {
        requirePublicApi();
        if (request == null || request.expectedVersion() == null || request.expectedVersion() < 0) {
            throw new ExecutionAccessException(400, "expected_version_required");
        }
        IdentityAccessVerifier.IdentityPrincipal principal = identityVerifier.requireRagAccess(authorization);
        ExecutionJpaEntity execution = lockedOwned(executionId, principal.userId());
        List<ExecutionStepJpaEntity> steps = steps(execution);
        if (execution.getStatus() != ExecutionStatus.WAITING_CLIENT_INPUT) {
            throw new ExecutionAccessException(409, "execution_not_waiting_for_client_input");
        }
        if (execution.getVersion() != request.expectedVersion()) {
            throw new ExecutionAccessException(409, "execution_version_conflict");
        }
        Instant now = clock.now();
        if (!execution.getExpiresAt().isAfter(now)) {
            throw new ExecutionAccessException(409, "client_input_expired");
        }
        String stepId = requireStepUuid(request.stepId());
        ExecutionStepJpaEntity step = steps.stream()
                .filter(candidate -> candidate.getId().toString().equals(stepId))
                .findFirst()
                .orElseThrow(() -> new ExecutionAccessException(404, "execution_step_not_found"));
        if (step.getActionType() != ExecutionActionType.UPLOAD_FILES
                || step.getStatus() != com.alicia.cloudstorage.ragexecution.domain.ExecutionStepStatus.WAITING_CLIENT_INPUT) {
            throw new ExecutionAccessException(409, "step_not_waiting_for_client_input");
        }

        String status = request.status() == null ? "" : request.status().trim().toUpperCase(Locale.ROOT);
        if ("SUCCEEDED".equals(status)) {
            List<Long> nodeIds = validateUploadedNodeIds(request.nodeIds());
            verifyUploadedNodes(execution, step, principal.userId(), nodeIds);
            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "SUCCEEDED");
            result.put("uploadedCount", nodeIds.size());
            var ids = result.putArray("nodeIds");
            nodeIds.forEach(ids::add);
            step.completeClientInput(result, now);
            finishOrQueueAfterClientInput(execution, steps, now);
            stepRepository.flush();
            executionRepository.flush();
            eventAppender.appendPublic(
                    execution.getId(),
                    "STEP_SUCCEEDED",
                    stepStatusPayload(execution, step, "SUCCEEDED"),
                    now
            );
            eventAppender.appendPublic(execution.getId(), "CLIENT_INPUT_COMPLETED", statusPayload(execution), now);
            eventAppender.appendPublic(
                    execution.getId(),
                    execution.getStatus() == ExecutionStatus.QUEUED
                            ? "EXECUTION_STEP_QUEUED"
                            : "EXECUTION_SUCCEEDED",
                    statusPayload(execution),
                    now
            );
            return response(execution, steps);
        }
        if (!Set.of("FAILED", "CANCELLED").contains(status)) {
            throw new ExecutionAccessException(422, "invalid_client_input_status");
        }
        String errorCode = safeClientErrorCode(request.errorCode(), status);
        step.failClientInput(errorCode, "Client file transfer did not complete.", now);
        int completed = (int) steps.stream()
                .filter(candidate -> candidate.getStatus()
                        == com.alicia.cloudstorage.ragexecution.domain.ExecutionStepStatus.SUCCEEDED)
                .count();
        ObjectNode summary = workflowSummary(completed, steps.size(), errorCode);
        if (completed > 0) {
            execution.completePartially("WORKFLOW_PARTIALLY_COMPLETED", summary, errorCode, now);
        } else {
            execution.fail(errorCode, "Client file transfer did not complete.", now);
        }
        stepRepository.flush();
        executionRepository.flush();
        eventAppender.appendPublic(
                execution.getId(),
                "STEP_FAILED",
                stepStatusPayload(execution, step, "FAILED"),
                now
        );
        eventAppender.appendPublic(execution.getId(), "CLIENT_INPUT_FAILED", statusPayload(execution), now);
        eventAppender.appendPublic(
                execution.getId(),
                execution.getStatus() == ExecutionStatus.PARTIALLY_SUCCEEDED
                        ? "EXECUTION_PARTIALLY_SUCCEEDED"
                        : "EXECUTION_FAILED",
                statusPayload(execution),
                now
        );
        return response(execution, steps);
    }

    private void verifyUploadedNodes(
            ExecutionJpaEntity execution,
            ExecutionStepJpaEntity step,
            long actorUserId,
            List<Long> nodeIds
    ) {
        long parentId = step.getResult() == null ? 0L : step.getResult().path("parentId").asLong(0L);
        if (parentId <= 0) {
            throw new ExecutionAccessException(409, "client_input_contract_invalid");
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("parentId", parentId);
        var ids = payload.putArray("nodeIds");
        nodeIds.forEach(ids::add);
        try {
            var verified = cloudActionGateway.dispatch(new CloudActionGateway.CloudActionCommand(
                    execution.getId(),
                    step.getId(),
                    actorUserId,
                    ExecutionActionType.UPLOAD_FILES,
                    ExecutionRegistrationValidator.PAYLOAD_SCHEMA_VERSION,
                    payload
            ));
            if (!"UPLOADS_VERIFIED".equals(verified.resultCode())) {
                throw new ExecutionAccessException(409, "uploaded_nodes_not_verified");
            }
        } catch (CloudActionDispatchException exception) {
            if (exception.retryable()) {
                throw new ExecutionAccessException(503, "upload_verification_unavailable");
            }
            throw new ExecutionAccessException(409, "uploaded_nodes_not_verified");
        }
    }

    private void finishOrQueueAfterClientInput(
            ExecutionJpaEntity execution,
            List<ExecutionStepJpaEntity> steps,
            Instant now
    ) {
        int completed = (int) steps.stream()
                .filter(step -> step.getStatus()
                        == com.alicia.cloudstorage.ragexecution.domain.ExecutionStepStatus.SUCCEEDED)
                .count();
        boolean hasPending = steps.stream().anyMatch(step -> step.getStatus()
                == com.alicia.cloudstorage.ragexecution.domain.ExecutionStepStatus.PENDING);
        if (hasPending) {
            execution.queueNextStep(now, now.plus(limits.maxQueueWait()));
            return;
        }
        execution.complete("WORKFLOW_SUCCEEDED", workflowSummary(completed, steps.size(), null), now);
    }

    private List<Long> validateUploadedNodeIds(List<Long> values) {
        if (values == null || values.isEmpty() || values.size() > limits.maxBatchNodes()) {
            throw new ExecutionAccessException(422, "invalid_uploaded_node_ids");
        }
        List<Long> normalized = List.copyOf(values);
        if (normalized.stream().anyMatch(value -> value == null || value <= 0)
                || normalized.stream().distinct().count() != normalized.size()) {
            throw new ExecutionAccessException(422, "invalid_uploaded_node_ids");
        }
        return normalized;
    }

    private String safeClientErrorCode(String value, String status) {
        String fallback = "CANCELLED".equals(status) ? "upload_cancelled" : "upload_failed";
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("upload_cancelled", "upload_failed", "client_input_unavailable").contains(normalized)) {
            throw new ExecutionAccessException(422, "invalid_client_input_error");
        }
        return normalized;
    }

    private ObjectNode workflowSummary(int completed, int total, String errorCode) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("completedSteps", completed);
        summary.put("totalSteps", total);
        if (errorCode == null) {
            summary.putNull("errorCode");
        } else {
            summary.put("errorCode", errorCode);
        }
        return summary;
    }

    @Transactional
    public ExecutionResponse cancel(String executionId, String authorization) {
        requirePublicApi();
        IdentityAccessVerifier.IdentityPrincipal principal = identityVerifier.requireRagAccess(authorization);
        ExecutionJpaEntity execution = lockedOwned(executionId, principal.userId());
        List<ExecutionStepJpaEntity> steps = steps(execution);
        if (execution.getStatus() == ExecutionStatus.CANCELLED) {
            return response(execution, steps);
        }
        if (execution.getStatus() != ExecutionStatus.PENDING_CONFIRMATION
                && execution.getStatus() != ExecutionStatus.QUEUED
                && execution.getStatus() != ExecutionStatus.WAITING_CLIENT_INPUT) {
            throw new ExecutionAccessException(409, "execution_not_cancellable");
        }
        Instant now = clock.now();
        execution.transitionTo(ExecutionStatus.CANCELLED, now);
        executionRepository.saveAndFlush(execution);
        eventAppender.appendPublic(execution.getId(), "EXECUTION_CANCELLED", statusPayload(execution), now);
        return response(execution, steps);
    }

    @Transactional(readOnly = true)
    public List<ExecutionEventResponse> events(String executionId, long after, String authorization) {
        requirePublicApi();
        if (after < 0) {
            throw new ExecutionAccessException(400, "invalid_event_sequence");
        }
        IdentityAccessVerifier.IdentityPrincipal principal = identityVerifier.requireRagAccess(authorization);
        owned(executionId, principal.userId());
        return eventsForOwnedExecution(executionId, after);
    }

    @Transactional(readOnly = true)
    public List<ExecutionEventResponse> eventsForOwnedExecution(String executionId, long after) {
        return eventRepository.findByExecutionIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(executionId, after)
                .stream()
                .map(this::eventResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long authorizeStream(String executionId, String authorization) {
        requirePublicApi();
        IdentityAccessVerifier.IdentityPrincipal principal = identityVerifier.requireRagAccess(authorization);
        owned(executionId, principal.userId());
        return principal.userId();
    }

    private void requirePublicApi() {
        if (!features.enabled()) {
            throw new ExecutionAccessException(404, "execution_api_disabled");
        }
    }

    private void requireConfirmation() {
        requirePublicApi();
        if (!features.publicConfirmEnabled()) {
            throw new ExecutionAccessException(404, "execution_confirmation_disabled");
        }
    }

    private ExecutionJpaEntity owned(String executionId, long userId) {
        String id = requireUuid(executionId);
        return executionRepository.findByIdAndOwnerUserId(id, userId)
                .orElseThrow(() -> new ExecutionAccessException(404, "execution_not_found"));
    }

    private ExecutionJpaEntity lockedOwned(String executionId, long userId) {
        String id = requireUuid(executionId);
        return executionRepository.lockByIdAndOwnerUserId(id, userId)
                .orElseThrow(() -> new ExecutionAccessException(404, "execution_not_found"));
    }

    private String requireUuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new ExecutionAccessException(400, "invalid_execution_id");
        }
    }

    private String requireStepUuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new ExecutionAccessException(400, "invalid_step_id");
        }
    }

    private List<ExecutionStepJpaEntity> steps(ExecutionJpaEntity execution) {
        return stepRepository.findByExecutionIdOrderByStepIndexAsc(execution.getId().toString());
    }

    private boolean isPreviouslyConfirmed(ExecutionStatus status) {
        return status == ExecutionStatus.QUEUED
                || status == ExecutionStatus.RUNNING
                || status == ExecutionStatus.RETRY_WAIT
                || status == ExecutionStatus.WAITING_CLIENT_INPUT
                || status == ExecutionStatus.SUCCEEDED
                || status == ExecutionStatus.PARTIALLY_SUCCEEDED;
    }

    private ObjectNode statusPayload(ExecutionJpaEntity execution) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status", execution.getStatus().name());
        payload.put("version", execution.getVersion());
        return payload;
    }

    private ObjectNode stepStatusPayload(
            ExecutionJpaEntity execution,
            ExecutionStepJpaEntity step,
            String status
    ) {
        ObjectNode payload = statusPayload(execution);
        payload.put("status", status);
        payload.put("stepId", step.getId().toString());
        payload.put("actionType", step.getActionType().name());
        payload.put("attempts", step.getAttempts());
        return payload;
    }

    private ExecutionResponse response(
            ExecutionJpaEntity execution,
            List<ExecutionStepJpaEntity> steps
    ) {
        return new ExecutionResponse(
                execution.getId().toString(),
                execution.getStatus().name(),
                execution.getVersion(),
                execution.getActionSummary(),
                execution.getRisk().name(),
                execution.getExpiresAt(),
                execution.getConfirmedAt(),
                execution.getQueuedAt(),
                execution.getStartedAt(),
                execution.getFinishedAt(),
                execution.getResultCode(),
                publicJson(execution.getResultSummary()),
                execution.getErrorCode(),
                steps.stream().map(this::stepResponse).toList()
        );
    }

    private ExecutionStepResponse stepResponse(ExecutionStepJpaEntity step) {
        return new ExecutionStepResponse(
                step.getId().toString(),
                step.getStepIndex(),
                step.getActionType().name(),
                step.getStatus().name(),
                step.getAttempts(),
                step.getStartedAt(),
                step.getFinishedAt(),
                step.getErrorCode(),
                publicJson(step.getResult())
        );
    }

    private ExecutionEventResponse eventResponse(ExecutionEventJpaEntity event) {
        return new ExecutionEventResponse(
                event.getSequenceNo(),
                event.getEventType(),
                publicJson(event.getPublicPayload()),
                event.getCreatedAt()
        );
    }

    private Object publicJson(com.fasterxml.jackson.databind.JsonNode value) {
        return value == null ? null : objectMapper.convertValue(value, Object.class);
    }
}
