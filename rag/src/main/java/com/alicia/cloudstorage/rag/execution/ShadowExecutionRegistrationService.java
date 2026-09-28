package com.alicia.cloudstorage.rag.execution;

import com.alicia.cloudstorage.rag.assistant.ActionPlan;
import com.alicia.cloudstorage.rag.assistant.ActionPlanStep;
import com.alicia.cloudstorage.rag.assistant.ActionPlanBinding;
import com.alicia.cloudstorage.rag.assistant.CandidateItem;
import com.alicia.cloudstorage.rag.assistant.CollectionActionSnapshotStore;
import com.alicia.cloudstorage.rag.assistant.ExecutionReference;
import com.alicia.cloudstorage.rag.assistant.IntentRecognitionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ShadowExecutionRegistrationService implements ShadowExecutionRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ShadowExecutionRegistrationService.class);
    private static final String PAYLOAD_SCHEMA_VERSION = "rag_execution_action_v1";

    private final RagExecutionRegistrationProperties properties;
    private final ExecutionRegistrationClient client;
    private final RegistrationPlanHasher hasher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final CollectionActionSnapshotStore collectionSnapshotStore;
    private final BatchSnapshotFingerprint batchSnapshotFingerprint;

    public ShadowExecutionRegistrationService(
            RagExecutionRegistrationProperties properties,
            ExecutionRegistrationClient client,
            RegistrationPlanHasher hasher,
            ObjectMapper objectMapper,
            Clock clock,
            CollectionActionSnapshotStore collectionSnapshotStore,
            BatchSnapshotFingerprint batchSnapshotFingerprint
    ) {
        this.properties = properties;
        this.client = client;
        this.hasher = hasher;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.collectionSnapshotStore = collectionSnapshotStore;
        this.batchSnapshotFingerprint = batchSnapshotFingerprint;
    }

    @Override
    public IntentRecognitionResponse registerIfEligible(
            IntentRecognitionResponse response,
            String authorizationHeader
    ) {
        if (!properties.enabled() || !eligible(response)) {
            return response;
        }
        ActionPlan plan = response.actionPlan();
        try {
            List<ExecutionRegistrationStep> steps = plan.steps().stream()
                    .map(step -> mapStep(plan, step, authorizationHeader))
                    .toList();
            Instant expiresAt = clock.instant().plus(properties.confirmationTtl());
            String conversationId = response.conversation() == null
                    ? plan.planId()
                    : response.conversation().conversationId();
            ExecutionRegistrationRequest draft = new ExecutionRegistrationRequest(
                    plan.version(),
                    response.id(),
                    conversationId,
                    response.intentId(),
                    plan.planId(),
                    "",
                    mapRisk(plan.risk()),
                    plan.summary(),
                    steps,
                    expiresAt
            );
            ExecutionRegistrationRequest request = new ExecutionRegistrationRequest(
                    draft.planSchemaVersion(),
                    draft.sourceResponseId(),
                    draft.conversationId(),
                    draft.intentId(),
                    draft.planId(),
                    hasher.hash(draft),
                    draft.risk(),
                    draft.summary(),
                    draft.steps(),
                    draft.expiresAt()
            );
            ExecutionRegistrationResponse registered = client.register(request, authorizationHeader);
            if (registered == null || registered.executionId() == null || registered.executionId().isBlank()) {
                throw new IllegalStateException("Execution registration returned no reference.");
            }
            return response.withExecutionReference(new ExecutionReference(
                    registered.executionId(),
                    registered.status(),
                    registered.version(),
                    registered.expiresAt()
            ));
        } catch (RuntimeException exception) {
            log.warn(
                    "RAG execution shadow registration failed: responseId={}, planId={}, category={}",
                    safeId(response.id()),
                    safeId(plan.planId()),
                    exception.getClass().getSimpleName()
            );
            return response;
        }
    }

    private boolean eligible(IntentRecognitionResponse response) {
        if (response == null || response.actionPlan() == null || response.executionReference() != null) {
            return false;
        }
        ActionPlan plan = response.actionPlan();
        boolean supportedState = "atomic".equals(plan.planKind())
                && List.of("review_required", "ready_to_execute").contains(plan.status())
                || "collection".equals(plan.planKind())
                && List.of("collection_review_required", "ready_to_execute").contains(plan.status())
                || "composite".equals(plan.planKind())
                && "composite.create_folder_then_upload".equals(plan.actionType())
                && List.of("review_required", "ready_to_execute").contains(plan.status());
        return supportedState
                && plan.planId() != null
                && !plan.planId().isBlank()
                && plan.steps() != null
                && !plan.steps().isEmpty()
                && plan.requiredClientFields().isEmpty();
    }

    private ExecutionRegistrationStep mapStep(
            ActionPlan plan,
            ActionPlanStep step,
            String authorizationHeader
    ) {
        if (step == null) {
            throw new IllegalArgumentException("Execution step is required.");
        }
        ObjectNode payload = switch (step.action()) {
            case "node.rename" -> renamePayload(step.params());
            case "node.trash" -> trashPayload(step.params());
            case "node.move" -> movePayload(step.params());
            case "folder.create" -> folderPayload(step.params());
            case "share.create" -> sharePayload(step.params());
            case "node.batch_trash", "node.batch_scoped_trash" -> batchTrashPayload(
                    plan, step, authorizationHeader
            );
            case "node.batch_move" -> batchMovePayload(plan, step, authorizationHeader);
            case "node.batch_rename_prefix" -> batchRenamePayload(plan, step, authorizationHeader);
            case "file.upload" -> uploadPayload(step);
            default -> throw new IllegalArgumentException("Unsupported shadow-registration action.");
        };
        return new ExecutionRegistrationStep(
                requiredIdentifier(step.stepId(), "stepId"),
                switch (step.action()) {
                    case "node.rename" -> "NODE_RENAME";
                    case "node.trash" -> "NODE_TRASH";
                    case "node.move" -> "NODE_MOVE";
                    case "folder.create" -> "FOLDER_CREATE";
                    case "share.create" -> "SHARE_CREATE";
                    case "node.batch_trash", "node.batch_scoped_trash" -> "NODE_BATCH_TRASH";
                    case "node.batch_move" -> "NODE_BATCH_MOVE";
                    case "node.batch_rename_prefix" -> "NODE_BATCH_RENAME";
                    case "file.upload" -> "UPLOAD_FILES";
                    default -> throw new IllegalArgumentException("Unsupported shadow-registration action.");
                },
                PAYLOAD_SCHEMA_VERSION,
                payload,
                step.dependsOn().stream().map(value -> requiredIdentifier(value, "dependsOn")).toList(),
                optionalIdentifier(step.outputKey(), "outputKey"),
                "file.upload".equals(step.action()) ? List.of("files") : List.of()
        );
    }

    private ObjectNode uploadPayload(ActionPlanStep step) {
        String reference = requiredText(step.params().get("parentId"), "parentId");
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^\\$steps\\.([a-z][a-z0-9_]{0,63})\\.outputs\\.([a-z][a-zA-Z0-9_]{0,63})$")
                .matcher(reference);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Upload parentId must reference a prior step output.");
        }
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode outputReference = payload.putObject("parentIdReference");
        outputReference.put("stepKey", matcher.group(1));
        outputReference.put("outputField", matcher.group(2));
        return payload;
    }

    private ObjectNode batchTrashPayload(
            ActionPlan plan,
            ActionPlanStep step,
            String authorizationHeader
    ) {
        CollectionSnapshot snapshot = collectionSnapshot(plan, authorizationHeader);
        ObjectNode payload = batchPayload(snapshot.candidates());
        if ("node.batch_scoped_trash".equals(step.action())) {
            Map<String, Object> filter = snapshot.binding().filter();
            ObjectNode scope = payload.putObject("scopedTrash");
            scope.put("selectorVersion", requiredText(filter.get("selectorVersion"), "selectorVersion"));
            putOptionalPositiveLong(scope, "sourceParentId", filter.get("sourceParentId"));
            scope.put("root", booleanValue(filter.get("sourceRoot"), false));
            ArrayNode nodeTypes = scope.putArray("nodeTypes");
            objectList(filter.get("nodeTypes"), "nodeTypes")
                    .forEach(value -> nodeTypes.add(requiredText(value, "nodeType").toUpperCase(Locale.ROOT)));
            scope.put("scopeFingerprint", requiredSha256(filter.get("scopeFingerprint"), "scopeFingerprint"));
            scope.put("impactFingerprint", requiredSha256(filter.get("impactFingerprint"), "impactFingerprint"));
            scope.put("expectedImpactCount", positiveInteger(
                    filter.get("expectedImpactCount"), "expectedImpactCount", 10_000
            ));
        }
        return payload;
    }

    private ObjectNode batchMovePayload(
            ActionPlan plan,
            ActionPlanStep step,
            String authorizationHeader
    ) {
        CollectionSnapshot snapshot = collectionSnapshot(plan, authorizationHeader);
        ObjectNode payload = batchPayload(snapshot.candidates());
        ActionPlanBinding target = plan.bindings().get("targetParent");
        Object destination = target != null && target.selectedCandidate() != null
                ? target.selectedCandidate().nodeId()
                : step.params().get("parentId");
        putOptionalPositiveLong(payload, "destinationParentId", destination);
        return payload;
    }

    private ObjectNode batchRenamePayload(
            ActionPlan plan,
            ActionPlanStep step,
            String authorizationHeader
    ) {
        CollectionSnapshot snapshot = collectionSnapshot(plan, authorizationHeader);
        String prefix = requiredText(step.params().get("prefix"), "prefix");
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode items = payload.putArray("items");
        for (CandidateItem candidate : snapshot.candidates()) {
            ObjectNode item = items.addObject();
            item.set("snapshot", snapshotNode(candidate));
            String newName = prefix + candidate.name();
            if (newName.length() > 255) {
                throw new IllegalArgumentException("A prefixed node name exceeds 255 characters.");
            }
            item.put("newName", newName);
        }
        payload.put("snapshotCount", snapshot.candidates().size());
        payload.put("snapshotFingerprint", batchSnapshotFingerprint.hash(snapshot.candidates()));
        return payload;
    }

    private ObjectNode batchPayload(List<CandidateItem> candidates) {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode items = payload.putArray("items");
        candidates.forEach(candidate -> items.add(snapshotNode(candidate)));
        payload.put("snapshotCount", candidates.size());
        payload.put("snapshotFingerprint", batchSnapshotFingerprint.hash(candidates));
        return payload;
    }

    private ObjectNode snapshotNode(CandidateItem candidate) {
        if (candidate.nodeId() == null || candidate.nodeId() <= 0) {
            throw new IllegalArgumentException("A batch candidate is missing nodeId.");
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("nodeId", candidate.nodeId());
        if (candidate.parentId() == null) {
            node.putNull("parentId");
        } else {
            node.put("parentId", candidate.parentId());
        }
        node.put("name", requiredText(candidate.name(), "name"));
        node.put("nodeType", requiredNodeType(candidate.type()));
        node.put("updatedAt", requiredOffsetTimestamp(candidate.updatedAt()));
        return node;
    }

    private CollectionSnapshot collectionSnapshot(ActionPlan plan, String authorizationHeader) {
        ActionPlanBinding binding = plan.bindings().values().stream()
                .filter(value -> "source_collection".equals(value.kind()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("sourceCollection binding is required."));
        String snapshotId = requiredText(binding.filter().get("snapshotId"), "snapshotId");
        List<CandidateItem> candidates = collectionSnapshotStore
                .load(snapshotId, plan.planId(), authorizationHeader)
                .orElseThrow(() -> new IllegalArgumentException("Collection snapshot is unavailable."));
        if (candidates.isEmpty() || binding.count() == null || binding.count() != candidates.size()) {
            throw new IllegalArgumentException("Collection snapshot count is inconsistent.");
        }
        return new CollectionSnapshot(binding, candidates);
    }

    private List<?> objectList(Object value, String field) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return list;
    }

    private int positiveInteger(Object value, String field, int maximum) {
        long parsed = positiveLong(value, field);
        if (parsed > maximum) {
            throw new IllegalArgumentException(field + " is too large.");
        }
        return (int) parsed;
    }

    private String requiredSha256(Object value, String field) {
        String text = requiredText(value, field).toLowerCase(Locale.ROOT);
        if (!text.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a SHA-256 digest.");
        }
        return text;
    }

    private String requiredNodeType(String value) {
        String normalized = requiredText(value, "nodeType").toUpperCase(Locale.ROOT);
        if (!List.of("FILE", "FOLDER").contains(normalized)) {
            throw new IllegalArgumentException("nodeType must be FILE or FOLDER.");
        }
        return normalized;
    }

    private String requiredOffsetTimestamp(String value) {
        String normalized = requiredText(value, "updatedAt");
        java.time.OffsetDateTime.parse(normalized);
        return normalized;
    }

    private String requiredIdentifier(String value, String field) {
        String normalized = requiredText(value, field);
        if (!normalized.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException(field + " is not a safe identifier.");
        }
        return normalized;
    }

    private String optionalIdentifier(String value, String field) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = requiredText(value, field);
        if (!normalized.matches("[a-z][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException(field + " is not a safe output identifier.");
        }
        return normalized;
    }

    private record CollectionSnapshot(ActionPlanBinding binding, List<CandidateItem> candidates) {
    }

    private ObjectNode renamePayload(Map<String, Object> params) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("nodeId", positiveLong(params.get("nodeId"), "nodeId"));
        putOptionalNonNegativeLong(payload, "expectedNodeVersion", params.get("expectedNodeVersion"));
        payload.put("newName", requiredText(params.get("name"), "name"));
        return payload;
    }

    private ObjectNode trashPayload(Map<String, Object> params) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("nodeId", positiveLong(params.get("nodeId"), "nodeId"));
        putOptionalNonNegativeLong(payload, "expectedNodeVersion", params.get("expectedNodeVersion"));
        return payload;
    }

    private ObjectNode movePayload(Map<String, Object> params) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("nodeId", positiveLong(params.get("nodeId"), "nodeId"));
        putOptionalNonNegativeLong(payload, "expectedNodeVersion", params.get("expectedNodeVersion"));
        putOptionalPositiveLong(payload, "destinationParentId", params.get("destinationParentId"));
        return payload;
    }

    private ObjectNode folderPayload(Map<String, Object> params) {
        ObjectNode payload = objectMapper.createObjectNode();
        putOptionalPositiveLong(payload, "parentId", params.get("parentId"));
        payload.put("folderName", requiredText(params.get("folderName"), "folderName"));
        return payload;
    }

    private ObjectNode sharePayload(Map<String, Object> params) {
        Object password = params.get("password");
        if (password != null && !String.valueOf(password).isBlank()) {
            throw new IllegalArgumentException(
                    "Password-protected shares require a dedicated secret-input channel."
            );
        }
        ObjectNode payload = objectMapper.createObjectNode();
        Object nodeIds = params.get("nodeIds");
        if (!(nodeIds instanceof List<?> ids) || ids.isEmpty()) {
            throw new IllegalArgumentException("nodeIds are required.");
        }
        ArrayNode array = payload.putArray("nodeIds");
        ids.forEach(value -> array.add(positiveLong(value, "nodeId")));
        putOptionalText(payload, "title", params.get("title"));
        putOptionalInteger(payload, "expiresInDays", params.get("expiresInDays"));
        payload.put("allowDownload", booleanValue(params.get("allowDownload"), true));
        payload.put("allowSave", booleanValue(params.get("allowSave"), true));
        return payload;
    }

    private void putOptionalPositiveLong(ObjectNode target, String field, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(field, positiveLong(value, field));
        }
    }

    private void putOptionalNonNegativeLong(ObjectNode target, String field, Object value) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number) || number.longValue() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative.");
        }
        target.put(field, number.longValue());
    }

    private void putOptionalText(ObjectNode target, String field, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(field, String.valueOf(value).trim());
        }
    }

    private void putOptionalInteger(ObjectNode target, String field, Object value) {
        if (value != null) {
            long parsed = positiveLong(value, field);
            if (parsed > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(field + " is too large.");
            }
            target.put(field, (int) parsed);
        }
    }

    private long positiveLong(Object value, String field) {
        if (!(value instanceof Number number) || number.longValue() <= 0) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
        return number.longValue();
    }

    private String requiredText(Object value, String field) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return String.valueOf(value).trim();
    }

    private boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }

    private String mapRisk(String risk) {
        return switch (risk == null ? "" : risk.trim().toLowerCase(Locale.ROOT)) {
            case "medium" -> "MEDIUM";
            case "high", "critical" -> "HIGH";
            default -> "LOW";
        };
    }

    private String safeId(String value) {
        if (value == null) {
            return "missing";
        }
        String normalized = value.replaceAll("[^A-Za-z0-9_-]", "_");
        return normalized.substring(0, Math.min(normalized.length(), 64));
    }
}
