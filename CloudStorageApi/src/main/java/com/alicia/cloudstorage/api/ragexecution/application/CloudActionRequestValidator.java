package com.alicia.cloudstorage.api.ragexecution.application;

import com.alicia.cloudstorage.api.ragexecution.api.CloudActionRequest;
import com.alicia.cloudstorage.api.ragexecution.config.RagExecutionCloudActionProperties;
import com.alicia.cloudstorage.api.ragexecution.domain.CloudActionPayload;
import com.alicia.cloudstorage.api.ragexecution.domain.CloudActionType;
import com.alicia.cloudstorage.api.ragexecution.domain.BatchNodeSnapshotCloud;
import com.alicia.cloudstorage.api.ragexecution.domain.BatchRenameItemCloud;
import com.alicia.cloudstorage.api.ragexecution.domain.FolderCreateCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.NodeMoveCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.NodeRenameCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.NodeTrashCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.NodeBatchMoveCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.NodeBatchRenameCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.NodeBatchTrashCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.ScopedTrashSnapshotCloud;
import com.alicia.cloudstorage.api.ragexecution.domain.ShareCreateCloudAction;
import com.alicia.cloudstorage.api.ragexecution.domain.UploadFilesVerificationCloudAction;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class CloudActionRequestValidator {

    public static final String PAYLOAD_SCHEMA_VERSION = "rag_execution_action_v1";

    private static final Set<String> PROHIBITED_FIELDS = Set.of(
            "url", "uri", "method", "path", "pathtemplate", "host", "hostname",
            "bucket", "objectkey", "storagekey", "authorization", "token", "password"
    );

    private final RagExecutionCloudActionProperties properties;
    private final CloudActionRequestHasher hasher;
    private final CloudBatchSnapshotFingerprint batchSnapshotFingerprint;
    private final Clock clock;

    public CloudActionRequestValidator(
            RagExecutionCloudActionProperties properties,
            CloudActionRequestHasher hasher,
            CloudBatchSnapshotFingerprint batchSnapshotFingerprint,
            Clock clock
    ) {
        this.properties = properties;
        this.hasher = hasher;
        this.batchSnapshotFingerprint = batchSnapshotFingerprint;
        this.clock = clock;
    }

    public ValidatedCloudAction validate(CloudActionRequest request) {
        if (request == null) {
            throw invalid("action_request_required");
        }
        String executionId = requireUuid(request.executionId(), "invalid_execution_id");
        String stepId = requireUuid(request.stepId(), "invalid_step_id");
        if (request.actorUserId() == null || request.actorUserId() <= 0) {
            throw invalid("invalid_actor_user_id");
        }
        requireExact(
                request.payloadSchemaVersion(),
                PAYLOAD_SCHEMA_VERSION,
                "unsupported_payload_schema"
        );
        CloudActionType actionType;
        try {
            actionType = CloudActionType.valueOf(requireText(
                    request.actionType(), 40, "invalid_action_type"
            ).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("invalid_action_type");
        }
        if (request.payload() == null || !request.payload().isObject()) {
            throw invalid("invalid_action_payload");
        }
        inspectPayload(request.payload());
        CloudActionPayload payload = parsePayload(actionType, request.payload());
        validateBatchFingerprint(payload);

        if (request.requestHash() == null || !request.requestHash().matches("[0-9a-f]{64}")) {
            throw invalid("invalid_request_hash");
        }
        if (!hasher.hash(request).equals(request.requestHash())) {
            throw invalid("request_hash_mismatch");
        }
        validateIssuedAt(request.issuedAt());
        return new ValidatedCloudAction(
                executionId,
                stepId,
                request.actorUserId(),
                actionType,
                PAYLOAD_SCHEMA_VERSION,
                request.requestHash(),
                payload
        );
    }

    private void validateBatchFingerprint(CloudActionPayload payload) {
        List<BatchNodeSnapshotCloud> snapshots;
        String expectedFingerprint;
        if (payload instanceof NodeBatchTrashCloudAction action) {
            snapshots = action.items();
            expectedFingerprint = action.snapshotFingerprint();
        } else if (payload instanceof NodeBatchMoveCloudAction action) {
            snapshots = action.items();
            expectedFingerprint = action.snapshotFingerprint();
        } else if (payload instanceof NodeBatchRenameCloudAction action) {
            snapshots = action.items().stream().map(BatchRenameItemCloud::snapshot).toList();
            expectedFingerprint = action.snapshotFingerprint();
        } else {
            return;
        }
        if (!batchSnapshotFingerprint.hash(snapshots).equals(expectedFingerprint)) {
            throw invalid("batch_snapshot_fingerprint_mismatch");
        }
    }

    private CloudActionPayload parsePayload(CloudActionType actionType, JsonNode payload) {
        try {
            return switch (actionType) {
                case NODE_RENAME -> {
                    requireFields(payload, Set.of("nodeId", "newName"), Set.of("expectedNodeVersion"));
                    yield new NodeRenameCloudAction(
                            positiveLong(payload, "nodeId"),
                            optionalNonNegativeLong(payload, "expectedNodeVersion"),
                            requiredText(payload, "newName", 255)
                    );
                }
                case NODE_TRASH -> {
                    requireFields(payload, Set.of("nodeId"), Set.of("expectedNodeVersion"));
                    yield new NodeTrashCloudAction(
                            positiveLong(payload, "nodeId"),
                            optionalNonNegativeLong(payload, "expectedNodeVersion")
                    );
                }
                case NODE_MOVE -> {
                    requireFields(
                            payload,
                            Set.of("nodeId"),
                            Set.of("expectedNodeVersion", "destinationParentId")
                    );
                    yield new NodeMoveCloudAction(
                            positiveLong(payload, "nodeId"),
                            optionalNonNegativeLong(payload, "expectedNodeVersion"),
                            optionalPositiveLong(payload, "destinationParentId")
                    );
                }
                case FOLDER_CREATE -> {
                    requireFields(payload, Set.of("folderName"), Set.of("parentId"));
                    yield new FolderCreateCloudAction(
                            optionalPositiveLong(payload, "parentId"),
                            requiredText(payload, "folderName", 255)
                    );
                }
                case SHARE_CREATE -> {
                    requireFields(
                            payload,
                            Set.of("nodeIds"),
                            Set.of("title", "expiresInDays", "allowDownload", "allowSave")
                    );
                    yield new ShareCreateCloudAction(
                            positiveLongArray(payload, "nodeIds", 20),
                            optionalText(payload, "title", 255),
                            optionalPositiveInteger(payload, "expiresInDays"),
                            optionalBoolean(payload, "allowDownload", true),
                            optionalBoolean(payload, "allowSave", true)
                    );
                }
                case NODE_BATCH_TRASH -> {
                    requireFields(
                            payload,
                            Set.of("items", "snapshotCount", "snapshotFingerprint"),
                            Set.of("scopedTrash")
                    );
                    yield new NodeBatchTrashCloudAction(
                            batchSnapshots(payload, "items"),
                            positiveInteger(payload, "snapshotCount", 500),
                            requiredSha256(payload, "snapshotFingerprint"),
                            optionalScopedTrash(payload, "scopedTrash")
                    );
                }
                case NODE_BATCH_MOVE -> {
                    requireFields(
                            payload,
                            Set.of("items", "snapshotCount", "snapshotFingerprint"),
                            Set.of("destinationParentId")
                    );
                    yield new NodeBatchMoveCloudAction(
                            batchSnapshots(payload, "items"),
                            positiveInteger(payload, "snapshotCount", 500),
                            requiredSha256(payload, "snapshotFingerprint"),
                            optionalPositiveLong(payload, "destinationParentId")
                    );
                }
                case NODE_BATCH_RENAME -> {
                    requireFields(
                            payload,
                            Set.of("items", "snapshotCount", "snapshotFingerprint"),
                            Set.of()
                    );
                    yield new NodeBatchRenameCloudAction(
                            batchRenameItems(payload, "items"),
                            positiveInteger(payload, "snapshotCount", 500),
                            requiredSha256(payload, "snapshotFingerprint")
                    );
                }
                case UPLOAD_FILES -> {
                    requireFields(payload, Set.of("parentId", "nodeIds"), Set.of());
                    yield new UploadFilesVerificationCloudAction(
                            positiveLong(payload, "parentId"),
                            positiveLongArray(payload, "nodeIds", 500)
                    );
                }
            };
        } catch (CloudActionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid("invalid_action_payload");
        }
    }

    private void validateIssuedAt(Instant issuedAt) {
        if (issuedAt == null) {
            throw invalid("invalid_action_issued_at");
        }
        Instant now = clock.instant();
        if (issuedAt.isBefore(now.minus(properties.requestMaxAge()))
                || issuedAt.isAfter(now.plus(properties.maximumClockSkew()))) {
            throw new CloudActionException(409, "stale_action_request");
        }
    }

    private void inspectPayload(JsonNode node) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                String key = entry.getKey().replace("_", "").replace("-", "")
                        .toLowerCase(Locale.ROOT);
                if (PROHIBITED_FIELDS.contains(key)) {
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
            String value = node.asText().toLowerCase(Locale.ROOT);
            if (value.contains("http://") || value.contains("https://")) {
                throw invalid("prohibited_action_url");
            }
        }
    }

    private static void requireFields(JsonNode payload, Set<String> required, Set<String> optional) {
        Set<String> actual = new HashSet<>(payload.propertyNames());
        if (!actual.containsAll(required)) {
            throw invalid("invalid_action_payload");
        }
        Set<String> allowed = new HashSet<>(required);
        allowed.addAll(optional);
        if (!allowed.containsAll(actual)) {
            throw invalid("unknown_action_field");
        }
    }

    private static long positiveLong(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw invalid("invalid_action_payload");
        }
        return value.longValue();
    }

    private static Long optionalPositiveLong(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return positiveLong(payload, field);
    }

    private static Long optionalNonNegativeLong(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw invalid("invalid_action_payload");
        }
        return value.longValue();
    }

    private static String requiredText(JsonNode payload, String field, int maximumLength) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid("invalid_action_payload");
        }
        String normalized = value.asText().trim();
        if (normalized.length() > maximumLength) {
            throw invalid("invalid_action_payload");
        }
        return normalized;
    }

    private static String optionalText(JsonNode payload, String field, int maximumLength) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull() || value.isTextual() && value.asText().isBlank()) {
            return null;
        }
        if (!value.isTextual() || value.asText().trim().length() > maximumLength) {
            throw invalid("invalid_action_payload");
        }
        return value.asText().trim();
    }

    private static Integer optionalPositiveInteger(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() <= 0) {
            throw invalid("invalid_action_payload");
        }
        return value.asInt();
    }

    private static boolean optionalBoolean(JsonNode payload, String field, boolean fallback) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isBoolean()) {
            throw invalid("invalid_action_payload");
        }
        return value.asBoolean();
    }

    private static List<Long> positiveLongArray(JsonNode payload, String field, int maximumItems) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isArray() || value.isEmpty() || value.size() > maximumItems) {
            throw invalid("invalid_action_payload");
        }
        List<Long> values = new ArrayList<>();
        value.forEach(item -> {
            if (!item.isIntegralNumber() || !item.canConvertToLong() || item.longValue() <= 0) {
                throw invalid("invalid_action_payload");
            }
            values.add(item.longValue());
        });
        return List.copyOf(values);
    }

    private static List<BatchNodeSnapshotCloud> batchSnapshots(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isArray() || value.isEmpty() || value.size() > 500) {
            throw invalid("invalid_action_payload");
        }
        List<BatchNodeSnapshotCloud> snapshots = new ArrayList<>();
        value.forEach(item -> snapshots.add(batchSnapshot(item)));
        return List.copyOf(snapshots);
    }

    private static BatchNodeSnapshotCloud batchSnapshot(JsonNode item) {
        if (item == null || !item.isObject()) {
            throw invalid("invalid_action_payload");
        }
        requireFields(item, Set.of("nodeId", "name", "nodeType", "updatedAt"), Set.of("parentId"));
        return new BatchNodeSnapshotCloud(
                positiveLong(item, "nodeId"),
                optionalPositiveLong(item, "parentId"),
                requiredText(item, "name", 255),
                requiredText(item, "nodeType", 16),
                requiredText(item, "updatedAt", 64)
        );
    }

    private static List<BatchRenameItemCloud> batchRenameItems(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isArray() || value.isEmpty() || value.size() > 500) {
            throw invalid("invalid_action_payload");
        }
        List<BatchRenameItemCloud> items = new ArrayList<>();
        value.forEach(item -> {
            if (!item.isObject()) {
                throw invalid("invalid_action_payload");
            }
            requireFields(item, Set.of("snapshot", "newName"), Set.of());
            items.add(new BatchRenameItemCloud(
                    batchSnapshot(item.get("snapshot")),
                    requiredText(item, "newName", 255)
            ));
        });
        return List.copyOf(items);
    }

    private static ScopedTrashSnapshotCloud optionalScopedTrash(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isObject()) {
            throw invalid("invalid_action_payload");
        }
        requireFields(
                value,
                Set.of(
                        "selectorVersion", "root", "nodeTypes", "scopeFingerprint",
                        "impactFingerprint", "expectedImpactCount"
                ),
                Set.of("sourceParentId")
        );
        JsonNode root = value.get("root");
        if (root == null || !root.isBoolean()) {
            throw invalid("invalid_action_payload");
        }
        JsonNode nodeTypes = value.get("nodeTypes");
        if (nodeTypes == null || !nodeTypes.isArray() || nodeTypes.isEmpty() || nodeTypes.size() > 2) {
            throw invalid("invalid_action_payload");
        }
        List<String> types = new ArrayList<>();
        nodeTypes.forEach(item -> {
            if (!item.isTextual()) {
                throw invalid("invalid_action_payload");
            }
            types.add(item.asText());
        });
        return new ScopedTrashSnapshotCloud(
                requiredText(value, "selectorVersion", 64),
                optionalPositiveLong(value, "sourceParentId"),
                root.asBoolean(),
                types,
                requiredSha256(value, "scopeFingerprint"),
                requiredSha256(value, "impactFingerprint"),
                positiveInteger(value, "expectedImpactCount", 10_000)
        );
    }

    private static int positiveInteger(JsonNode payload, String field, int maximum) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() <= 0 || value.intValue() > maximum) {
            throw invalid("invalid_action_payload");
        }
        return value.intValue();
    }

    private static String requiredSha256(JsonNode payload, String field) {
        String value = requiredText(payload, field, 64).toLowerCase(Locale.ROOT);
        if (!value.matches("[0-9a-f]{64}")) {
            throw invalid("invalid_action_payload");
        }
        return value;
    }

    private static String requireUuid(String value, String errorCode) {
        try {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            UUID uuid = UUID.fromString(normalized);
            if (!uuid.toString().equals(normalized)) {
                throw invalid(errorCode);
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw invalid(errorCode);
        }
    }

    private static String requireText(String value, int maximumLength, String errorCode) {
        if (value == null || value.isBlank() || value.trim().length() > maximumLength) {
            throw invalid(errorCode);
        }
        return value.trim();
    }

    private static void requireExact(String actual, String expected, String errorCode) {
        if (!expected.equals(actual)) {
            throw invalid(errorCode);
        }
    }

    private static CloudActionException invalid(String errorCode) {
        return new CloudActionException(422, errorCode);
    }

    public record ValidatedCloudAction(
            String executionId,
            String stepId,
            long actorUserId,
            CloudActionType actionType,
            String payloadSchemaVersion,
            String requestHash,
            CloudActionPayload payload
    ) {
    }
}
