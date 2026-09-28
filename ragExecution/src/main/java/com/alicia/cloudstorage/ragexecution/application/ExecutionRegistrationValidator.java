package com.alicia.cloudstorage.ragexecution.application;

import com.alicia.cloudstorage.ragexecution.api.internal.RegisterExecutionRequest;
import com.alicia.cloudstorage.ragexecution.api.internal.RegisterExecutionStepRequest;
import com.alicia.cloudstorage.ragexecution.config.RagExecutionLimitsProperties;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionRisk;
import com.alicia.cloudstorage.ragexecution.domain.action.ExecutionActionPayload;
import com.alicia.cloudstorage.ragexecution.domain.action.FolderCreateAction;
import com.alicia.cloudstorage.ragexecution.domain.action.BatchNodeSnapshot;
import com.alicia.cloudstorage.ragexecution.domain.action.NodeBatchMoveAction;
import com.alicia.cloudstorage.ragexecution.domain.action.NodeBatchRenameAction;
import com.alicia.cloudstorage.ragexecution.domain.action.NodeBatchTrashAction;
import com.alicia.cloudstorage.ragexecution.domain.action.UploadFilesAction;
import com.alicia.cloudstorage.ragexecution.domain.action.NodeMoveAction;
import com.alicia.cloudstorage.ragexecution.domain.action.NodeRenameAction;
import com.alicia.cloudstorage.ragexecution.domain.action.NodeTrashAction;
import com.alicia.cloudstorage.ragexecution.domain.action.ShareCreateAction;
import com.alicia.cloudstorage.ragexecution.port.ExecutionClock;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ExecutionRegistrationValidator {

    public static final String PLAN_SCHEMA_VERSION = "action_plan_v2";
    public static final String PAYLOAD_SCHEMA_VERSION = "rag_execution_action_v1";

    private static final Set<ExecutionActionType> SUPPORTED_ACTIONS = Set.of(
            ExecutionActionType.NODE_RENAME,
            ExecutionActionType.NODE_TRASH,
            ExecutionActionType.NODE_MOVE,
            ExecutionActionType.FOLDER_CREATE,
            ExecutionActionType.SHARE_CREATE,
            ExecutionActionType.NODE_BATCH_TRASH,
            ExecutionActionType.NODE_BATCH_MOVE,
            ExecutionActionType.NODE_BATCH_RENAME,
            ExecutionActionType.UPLOAD_FILES
    );
    private static final Set<String> PROHIBITED_FIELDS = Set.of(
            "url", "uri", "method", "path", "pathtemplate", "host", "hostname",
            "bucket", "objectkey", "storagekey", "authorization", "token", "password"
    );

    private final ObjectMapper objectMapper;
    private final RagExecutionLimitsProperties limits;
    private final ExecutionClock clock;
    private final CanonicalJsonHasher hasher;
    private final BatchSnapshotFingerprint batchSnapshotFingerprint;

    public ExecutionRegistrationValidator(
            ObjectMapper objectMapper,
            RagExecutionLimitsProperties limits,
            ExecutionClock clock,
            CanonicalJsonHasher hasher,
            BatchSnapshotFingerprint batchSnapshotFingerprint
    ) {
        this.objectMapper = objectMapper;
        this.limits = limits;
        this.clock = clock;
        this.hasher = hasher;
        this.batchSnapshotFingerprint = batchSnapshotFingerprint;
    }

    public ValidatedRegistration validate(RegisterExecutionRequest request) {
        if (request == null) {
            throw invalid("plan_request_required");
        }
        ensureSerializedSize(request);
        requireExact(request.planSchemaVersion(), PLAN_SCHEMA_VERSION, "unsupported_plan_schema");
        requireText(request.sourceResponseId(), 128, "invalid_source_response_id");
        requireText(request.conversationId(), 128, "invalid_conversation_id");
        requireText(request.intentId(), 128, "invalid_intent_id");
        requireText(request.planId(), 128, "invalid_plan_id");
        requireText(request.summary(), 1000, "invalid_plan_summary");
        rejectUrlText(request.summary());

        if (request.planHash() == null || !request.planHash().matches("[0-9a-f]{64}")) {
            throw invalid("invalid_plan_hash");
        }
        String actualHash = hasher.planHash(request);
        if (!actualHash.equals(request.planHash())) {
            throw invalid("plan_hash_mismatch");
        }

        ExecutionRisk risk;
        try {
            risk = ExecutionRisk.valueOf(requireText(request.risk(), 16, "invalid_plan_risk")
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("invalid_plan_risk");
        }

        Instant now = clock.now();
        if (request.expiresAt() == null
                || !request.expiresAt().isAfter(now)
                || request.expiresAt().isAfter(now.plus(limits.confirmationTtl()).plusSeconds(5))) {
            throw invalid("invalid_plan_expiry");
        }
        if (request.steps().isEmpty() || request.steps().size() > limits.maxSteps()) {
            throw invalid("invalid_plan_step_count");
        }

        List<ValidatedStep> steps = new ArrayList<>();
        for (RegisterExecutionStepRequest step : request.steps()) {
            steps.add(validateStep(step));
        }
        validateStepGraph(steps);
        return new ValidatedRegistration(risk, List.copyOf(steps));
    }

    private ValidatedStep validateStep(RegisterExecutionStepRequest step) {
        if (step == null || step.payload() == null) {
            throw invalid("invalid_action_payload");
        }
        JsonNode payload = objectMapper.valueToTree(step.payload());
        if (!payload.isObject()) {
            throw invalid("invalid_action_payload");
        }
        requireExact(step.payloadSchemaVersion(), PAYLOAD_SCHEMA_VERSION, "unsupported_payload_schema");
        ExecutionActionType actionType;
        try {
            actionType = ExecutionActionType.valueOf(requireText(
                    step.actionType(), 40, "invalid_action_type"
            ).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("invalid_action_type");
        }
        if (!SUPPORTED_ACTIONS.contains(actionType)) {
            throw invalid("unsupported_action_type");
        }
        inspectPayload(payload);
        ExecutionActionPayload typedPayload = deserializePayload(actionType, payload);
        if (typedPayload.actionType() != actionType) {
            throw invalid("action_payload_mismatch");
        }
        validateBatchSnapshot(typedPayload);
        String stepKey = requireIdentifier(step.stepKey(), "invalid_step_key");
        List<String> dependsOn = safeIdentifiers(step.dependsOn(), "invalid_step_dependency");
        String outputKey = optionalOutputIdentifier(step.outputKey());
        List<String> requiredClientFields = safeIdentifiers(
                step.requiredClientFields(), "invalid_required_client_field"
        );
        if (actionType == ExecutionActionType.UPLOAD_FILES) {
            if (!requiredClientFields.equals(List.of("files"))) {
                throw invalid("invalid_required_client_field");
            }
        } else if (!requiredClientFields.isEmpty()) {
            throw invalid("unexpected_required_client_field");
        }
        return new ValidatedStep(
                stepKey,
                actionType,
                payload,
                hasher.payloadHash(payload),
                dependsOn,
                outputKey,
                requiredClientFields,
                typedPayload
        );
    }

    private ExecutionActionPayload deserializePayload(ExecutionActionType actionType, JsonNode payload) {
        Class<? extends ExecutionActionPayload> type = switch (actionType) {
            case NODE_RENAME -> NodeRenameAction.class;
            case NODE_TRASH -> NodeTrashAction.class;
            case NODE_MOVE -> NodeMoveAction.class;
            case FOLDER_CREATE -> FolderCreateAction.class;
            case SHARE_CREATE -> ShareCreateAction.class;
            case NODE_BATCH_TRASH -> NodeBatchTrashAction.class;
            case NODE_BATCH_MOVE -> NodeBatchMoveAction.class;
            case NODE_BATCH_RENAME -> NodeBatchRenameAction.class;
            case UPLOAD_FILES -> UploadFilesAction.class;
            default -> throw invalid("unsupported_action_type");
        };
        try {
            return objectMapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(payload);
        } catch (Exception exception) {
            throw invalid("invalid_action_payload");
        }
    }

    private void validateStepGraph(List<ValidatedStep> steps) {
        java.util.LinkedHashMap<String, ValidatedStep> priorByKey = new java.util.LinkedHashMap<>();
        java.util.HashSet<String> outputKeys = new java.util.HashSet<>();
        for (ValidatedStep step : steps) {
            if (priorByKey.containsKey(step.stepKey())) {
                throw invalid("duplicate_step_key");
            }
            for (String dependency : step.dependsOn()) {
                if (!priorByKey.containsKey(dependency)) {
                    throw invalid("invalid_step_dependency");
                }
            }
            if (!step.outputKey().isBlank() && !outputKeys.add(step.outputKey())) {
                throw invalid("duplicate_output_key");
            }
            if (step.typedPayload() instanceof UploadFilesAction upload) {
                String sourceKey = upload.parentIdReference().stepKey();
                ValidatedStep source = priorByKey.get(sourceKey);
                if (source == null
                        || !step.dependsOn().contains(sourceKey)
                        || source.actionType() != ExecutionActionType.FOLDER_CREATE
                        || !"nodeId".equals(upload.parentIdReference().outputField())) {
                    throw invalid("unsafe_step_output_reference");
                }
            }
            priorByKey.put(step.stepKey(), step);
        }
    }

    private static List<String> safeIdentifiers(List<String> values, String errorCode) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = values.stream()
                .map(value -> requireIdentifier(value, errorCode))
                .toList();
        if (normalized.stream().distinct().count() != normalized.size()) {
            throw invalid(errorCode);
        }
        return List.copyOf(normalized);
    }

    private static String requireIdentifier(String value, String errorCode) {
        String normalized = requireText(value, 64, errorCode);
        if (!normalized.matches("[a-z][a-z0-9_]{0,63}")) {
            throw invalid(errorCode);
        }
        return normalized;
    }

    private static String optionalOutputIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() > 64 || !normalized.matches("[a-z][A-Za-z0-9_]{0,63}")) {
            throw invalid("invalid_output_key");
        }
        return normalized;
    }

    private void validateBatchSnapshot(ExecutionActionPayload payload) {
        List<BatchNodeSnapshot> snapshots;
        int declaredCount;
        String declaredFingerprint;
        if (payload instanceof NodeBatchTrashAction action) {
            snapshots = action.items();
            declaredCount = action.snapshotCount();
            declaredFingerprint = action.snapshotFingerprint();
        } else if (payload instanceof NodeBatchMoveAction action) {
            snapshots = action.items();
            declaredCount = action.snapshotCount();
            declaredFingerprint = action.snapshotFingerprint();
        } else if (payload instanceof NodeBatchRenameAction action) {
            snapshots = action.items().stream().map(item -> item.snapshot()).toList();
            declaredCount = action.snapshotCount();
            declaredFingerprint = action.snapshotFingerprint();
        } else {
            return;
        }
        if (snapshots.size() > limits.maxBatchNodes()) {
            throw invalid("batch_node_limit_exceeded");
        }
        if (declaredCount != snapshots.size()) {
            throw invalid("batch_snapshot_count_mismatch");
        }
        if (!batchSnapshotFingerprint.hash(snapshots).equals(declaredFingerprint)) {
            throw invalid("batch_snapshot_fingerprint_mismatch");
        }
    }

    private void inspectPayload(JsonNode node) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                String normalizedKey = entry.getKey().replace("_", "").replace("-", "")
                        .toLowerCase(Locale.ROOT);
                if (PROHIBITED_FIELDS.contains(normalizedKey)) {
                    throw invalid("prohibited_action_field");
                }
                inspectPayload(entry.getValue());
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(this::inspectPayload);
            return;
        }
        if (node.isTextual()) {
            rejectUrlText(node.textValue());
        }
    }

    private void rejectUrlText(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (normalized.contains("http://") || normalized.contains("https://")) {
            throw invalid("prohibited_action_url");
        }
    }

    private void ensureSerializedSize(RegisterExecutionRequest request) {
        try {
            if (objectMapper.writeValueAsBytes(request).length > limits.maxPlanBytes()) {
                throw new PlanRegistrationException(413, "plan_payload_too_large");
            }
        } catch (PlanRegistrationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PlanRegistrationException(422, "invalid_plan_payload", exception);
        }
    }

    private static String requireText(String value, int maximum, String errorCode) {
        if (value == null || value.isBlank() || value.trim().length() > maximum) {
            throw invalid(errorCode);
        }
        return value.trim();
    }

    private static void requireExact(String actual, String expected, String errorCode) {
        if (!expected.equals(actual)) {
            throw invalid(errorCode);
        }
    }

    private static PlanRegistrationException invalid(String errorCode) {
        return new PlanRegistrationException(422, errorCode);
    }

    public record ValidatedRegistration(ExecutionRisk risk, List<ValidatedStep> steps) {
    }

    public record ValidatedStep(
            String stepKey,
            ExecutionActionType actionType,
            JsonNode payload,
            String payloadHash,
            List<String> dependsOn,
            String outputKey,
            List<String> requiredClientFields,
            ExecutionActionPayload typedPayload
    ) {
    }
}
