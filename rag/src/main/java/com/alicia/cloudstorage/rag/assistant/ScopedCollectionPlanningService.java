package com.alicia.cloudstorage.rag.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ScopedCollectionPlanningService {

    private static final String BINDING_ROLE = "_binding_role";
    private static final String PLAN_ID = "_plan_id";
    private static final String SNAPSHOT_ID = "_snapshot_id";
    private static final String SNAPSHOT_COUNT = "_snapshot_count";
    private static final String SOURCE_FOLDER_ROLE = "sourceFolder";
    private static final String TARGET_PARENT_ROLE = "targetParent";
    private static final Set<String> ROOT_ALIASES = Set.of(
            "根", "根目录", "根文件夹", "云盘根目录", "我的云盘", "顶层目录", "最外层", "/"
    );

    private final CandidateSearchPort candidateSearchPort;
    private final CollectionPreviewPort collectionPreviewPort;
    private final CollectionActionSnapshotStore snapshotStore;
    private final int previewItems;
    private final int maxCollectionItems;

    public ScopedCollectionPlanningService(
            CandidateSearchPort candidateSearchPort,
            CollectionPreviewPort collectionPreviewPort,
            CollectionActionSnapshotStore snapshotStore,
            @Value("${alicia.rag.collection-preview.display-items:20}") int previewItems,
            @Value("${alicia.rag.collection-preview.max-scan-items:500}") int maxCollectionItems
    ) {
        this.candidateSearchPort = candidateSearchPort;
        this.collectionPreviewPort = collectionPreviewPort;
        this.snapshotStore = snapshotStore;
        this.previewItems = Math.max(1, Math.min(50, previewItems));
        this.maxCollectionItems = Math.max(this.previewItems, Math.min(500, maxCollectionItems));
    }

    public IntentRecognitionResponse applySelection(
            IntentRecognitionResponse response,
            AssistantConversationState conversation,
            CandidateBindingResult selectedBinding
    ) {
        if (response == null || conversation == null || selectedBinding == null || selectedBinding.selectedCandidate() == null) {
            return response;
        }
        String role = stringValue(conversation.entities().get(BINDING_ROLE));
        if (!List.of(SOURCE_FOLDER_ROLE, TARGET_PARENT_ROLE).contains(role)) {
            return response;
        }
        CandidateItem selected = selectedBinding.selectedCandidate();
        Map<String, Object> entities = new LinkedHashMap<>(response.entities());
        entities.remove(BINDING_ROLE);
        if (SOURCE_FOLDER_ROLE.equals(role)) {
            putId(entities, "source_parent_id", selected.nodeId());
            entities.put("source_folder_path", selected.path());
        } else {
            putId(entities, "target_parent_id", selected.nodeId());
            entities.put("target_folder_path", selected.path());
            if (selected.nodeId() == null) {
                entities.put("target_is_root", true);
            }
        }
        return response.withPlanningState(
                Map.copyOf(entities),
                selectedBinding,
                response.actionPlan(),
                "wait_for_backend_binding",
                "已按你的选择锁定目录，我继续核对完整操作范围。"
        );
    }

    public void complete(String planId, String authorizationHeader) {
        snapshotStore.removeByPlanId(planId, authorizationHeader);
    }

    public boolean acceptsSelection(AssistantConversationState conversation, AssistantClientEvent event) {
        if (conversation == null || event == null) {
            return false;
        }
        String expectedRole = stringValue(conversation.entities().get(BINDING_ROLE));
        String expectedPlanId = stringValue(conversation.entities().get(PLAN_ID));
        return !expectedRole.isBlank()
                && (event.bindingKey().isBlank() || expectedRole.equals(event.bindingKey()))
                && (event.planId().isBlank() || expectedPlanId.equals(event.planId()));
    }

    public IntentRecognitionResponse plan(
            IntentRecognitionResponse response,
            AssistantConversationState conversation,
            String authorizationHeader,
            AssistantClientContext clientContext
    ) {
        if (!isScopedCollection(response)) {
            return response;
        }

        Map<String, Object> entities = new LinkedHashMap<>(response.entities());
        String actionType = response.actionDraft().type();
        boolean move = "collection.move".equals(actionType);
        String storedPlanId = stringValue(entities.get(PLAN_ID));
        String storedSnapshotId = stringValue(entities.get(SNAPSHOT_ID));
        if (!storedPlanId.isBlank() && !storedSnapshotId.isBlank()) {
            List<CandidateItem> storedCandidates = snapshotStore
                    .load(storedSnapshotId, storedPlanId, authorizationHeader)
                    .orElse(null);
            if (storedCandidates == null) {
                return collectionUnavailable(
                        response,
                        entities,
                        storedPlanId,
                        actionType,
                        "操作预览已经过期，请重新描述操作范围并再次确认。"
                );
            }
            CandidateItem sourceFolder = CollectionOperationSelectorResolver.SOURCE_PREVIOUS_RESULTS.equals(
                    stringValue(entities.get("source_kind"))
            ) ? null : storedFolder(entities, true);
            CandidateItem targetFolder = move ? storedFolder(entities, false) : null;
            if (move && targetFolder == null) {
                return bindingFailed(
                        response,
                        entities,
                        storedPlanId,
                        TARGET_PARENT_ROLE,
                        BindingResolution.failed("目标目录信息已经失效，请重新选择目标目录。"),
                        actionType
                );
            }
            Map<String, Object> storedFilter = sourceFilter(entities);
            storedFilter.put("snapshotId", storedSnapshotId);
            storedFilter.put("snapshotCount", storedCandidates.size());
            return reviewReady(
                    response,
                    entities,
                    storedPlanId,
                    actionType,
                    storedCandidates,
                    storedFilter,
                    sourceFolder,
                    targetFolder
            );
        }
        String planId = "ap_" + response.id();

        BindingResolution targetResolution = move
                ? resolveTarget(entities, authorizationHeader, clientContext)
                : BindingResolution.notRequired();
        if (targetResolution.needsSelection()) {
            entities.put(PLAN_ID, planId);
            return selectionRequired(response, entities, planId, TARGET_PARENT_ROLE, targetResolution, actionType);
        }
        if (targetResolution.failed()) {
            return bindingFailed(response, entities, planId, TARGET_PARENT_ROLE, targetResolution, actionType);
        }
        if (move) {
            rememberResolvedFolder(entities, targetResolution.candidate(), false);
        }

        BindingResolution sourceFolderResolution = resolveSourceFolder(
                entities,
                conversation,
                authorizationHeader,
                clientContext
        );
        if (sourceFolderResolution.needsSelection()) {
            entities.put(PLAN_ID, planId);
            return selectionRequired(response, entities, planId, SOURCE_FOLDER_ROLE, sourceFolderResolution, actionType);
        }
        if (sourceFolderResolution.failed()) {
            return bindingFailed(response, entities, planId, SOURCE_FOLDER_ROLE, sourceFolderResolution, actionType);
        }
        if (sourceFolderResolution.required()) {
            rememberResolvedFolder(entities, sourceFolderResolution.candidate(), true);
        }

        List<CandidateItem> sourceCandidates;
        String sourceKind = stringValue(entities.get("source_kind"));
        Map<String, Object> sourceFilter = sourceFilter(entities);

        if (CollectionOperationSelectorResolver.SOURCE_PREVIOUS_RESULTS.equals(sourceKind)) {
            sourceCandidates = previousCandidates(conversation, stringValue(entities.get("source_node_type")));
            sourceFilter.put("sourceReference", "previousResults");
        } else {
            Long sourceParentId = longValue(entities.get("source_parent_id"));
            boolean sourceRoot = booleanValue(entities.get("source_is_root"));
            if (sourceParentId == null && !sourceRoot) {
                return bindingFailed(
                        response,
                        entities,
                        planId,
                        SOURCE_FOLDER_ROLE,
                        BindingResolution.failed("没有可靠地定位到源目录，请补充源目录的完整路径。"),
                        actionType
                );
            }
            sourceFilter.put("parentId", sourceParentId == null ? "" : sourceParentId);
            sourceFilter.put("root", sourceRoot);
            sourceFilter.put("includeFolders", !"FILE".equals(stringValue(entities.get("source_node_type"))));
            CollectionPreviewResult preview = collectionPreviewPort.preview(new CollectionPreviewRequest(
                    actionType,
                    Map.copyOf(sourceFilter),
                    maxCollectionItems,
                    maxCollectionItems,
                    authorizationHeader
            ));
            if (!"preview_ready".equals(preview.status()) || !preview.exactCount()) {
                return collectionUnavailable(response, entities, planId, actionType, preview.message());
            }
            sourceCandidates = preview.candidates();
        }

        sourceCandidates = sourceCandidates.stream()
                .filter(candidate -> candidate.nodeId() != null)
                .distinct()
                .toList();
        if (sourceCandidates.isEmpty()) {
            return collectionUnavailable(response, entities, planId, actionType, "这个范围内没有可处理的项目。操作不会执行。");
        }
        if (sourceCandidates.size() > maxCollectionItems) {
            return collectionUnavailable(
                    response,
                    entities,
                    planId,
                    actionType,
                    "匹配项目超过 " + maxCollectionItems + " 个，请缩小范围后再试。"
            );
        }
        if (move && sameParent(sourceCandidates, targetResolution.candidate())) {
            return collectionUnavailable(response, entities, planId, actionType, "这些项目已经在目标目录中，无需重复移动。");
        }

        String snapshotId = snapshotStore.save(planId, authorizationHeader, sourceCandidates);
        sourceFilter.put("snapshotId", snapshotId);
        sourceFilter.put("snapshotCount", sourceCandidates.size());
        entities.put(PLAN_ID, planId);
        entities.put(SNAPSHOT_ID, snapshotId);
        entities.put(SNAPSHOT_COUNT, sourceCandidates.size());
        return reviewReady(
                response,
                entities,
                planId,
                actionType,
                sourceCandidates,
                sourceFilter,
                sourceFolderResolution.candidate(),
                targetResolution.candidate()
        );
    }

    private IntentRecognitionResponse reviewReady(
            IntentRecognitionResponse response,
            Map<String, Object> entities,
            String planId,
            String actionType,
            List<CandidateItem> sourceCandidates,
            Map<String, Object> sourceFilter,
            CandidateItem sourceFolder,
            CandidateItem targetFolder
    ) {
        ActionPlan plan = readyPlan(
                planId,
                actionType,
                entities,
                sourceCandidates,
                sourceFilter,
                sourceFolder,
                targetFolder
        );
        CandidateBindingResult focusBinding = new CandidateBindingResult(
                "search_results_ready",
                "collection-snapshot",
                sourceDescription(entities),
                stringValue(entities.get("source_node_type")),
                sourceCandidates.stream().limit(previewItems).toList(),
                "已锁定 " + sourceCandidates.size() + " 个待处理项目。"
        );
        entities.remove(BINDING_ROLE);
        return response.withPlanningState(
                Map.copyOf(entities),
                focusBinding,
                plan,
                "wait_for_user_confirmation",
                actionType.equals("collection.move")
                        ? "我已核对源目录、完整文件集合和目标目录。请确认预览，确认后再执行移动。"
                        : "我已核对完整文件集合。请确认预览，确认后这些项目才会移入回收站。"
        );
    }

    private BindingResolution resolveTarget(
            Map<String, Object> entities,
            String authorizationHeader,
            AssistantClientContext clientContext
    ) {
        if (booleanValue(entities.get("target_is_root"))) {
            return BindingResolution.resolved(rootCandidate());
        }
        Long targetId = longValue(entities.get("target_parent_id"));
        if (targetId != null) {
            return BindingResolution.resolved(new CandidateItem(
                    targetId, null, stringValue(entities.get("target_folder")), "FOLDER", 0L, "", "", "",
                    stringValue(entities.get("target_folder_path")), List.of()
            ));
        }
        String targetFolder = stringValue(entities.get("target_folder"));
        if (isRootAlias(targetFolder)) {
            return BindingResolution.resolved(rootCandidate());
        }
        return searchFolder(targetFolder, TARGET_PARENT_ROLE, authorizationHeader, clientContext);
    }

    private BindingResolution resolveSourceFolder(
            Map<String, Object> entities,
            AssistantConversationState conversation,
            String authorizationHeader,
            AssistantClientContext clientContext
    ) {
        String sourceKind = stringValue(entities.get("source_kind"));
        if (CollectionOperationSelectorResolver.SOURCE_PREVIOUS_RESULTS.equals(sourceKind)) {
            return BindingResolution.notRequired();
        }
        if (booleanValue(entities.get("source_is_root"))) {
            return BindingResolution.resolved(rootCandidate());
        }
        Long sourceId = longValue(entities.get("source_parent_id"));
        if (sourceId != null) {
            return BindingResolution.resolved(new CandidateItem(
                    sourceId, null, stringValue(entities.get("source_folder")), "FOLDER", 0L, "", "", "",
                    stringValue(entities.get("source_folder_path")), List.of()
            ));
        }
        if (CollectionOperationSelectorResolver.SOURCE_CONTEXT_FOLDER.equals(sourceKind)) {
            CandidateItem contextFolder = contextFolder(conversation);
            return contextFolder == null
                    ? BindingResolution.failed("无法从上一轮结果确定“这个文件夹”，请直接说出目录名称或完整路径。")
                    : BindingResolution.resolved(contextFolder);
        }
        String sourceFolder = stringValue(entities.get("source_folder"));
        if (isRootAlias(sourceFolder)) {
            return BindingResolution.resolved(rootCandidate());
        }
        return searchFolder(sourceFolder, SOURCE_FOLDER_ROLE, authorizationHeader, clientContext);
    }

    private BindingResolution searchFolder(
            String query,
            String role,
            String authorizationHeader,
            AssistantClientContext clientContext
    ) {
        if (query.isBlank()) {
            return BindingResolution.failed("缺少目录名称，请补充目录名称或完整路径。");
        }
        AssistantClientContext context = clientContext == null ? AssistantClientContext.empty() : clientContext;
        CandidateBindingResult result = candidateSearchPort.search(new CandidateSearchRequest(
                "collection_scope_binding",
                "collection",
                "FOLDER",
                role,
                query,
                FileQueryPlanResolver.NAME_SEARCH,
                FileQueryPlanResolver.SCOPE_ALL,
                "",
                context.currentFolderId(),
                context.currentFolderPath(),
                10,
                authorizationHeader
        ));
        if (result.candidates().isEmpty()) {
            return BindingResolution.failed("没有找到目录“" + query + "”，请检查名称或提供完整路径。", result);
        }
        if (result.candidates().size() > 1) {
            return BindingResolution.selection(result);
        }
        return BindingResolution.resolved(result.candidates().getFirst());
    }

    private IntentRecognitionResponse selectionRequired(
            IntentRecognitionResponse response,
            Map<String, Object> entities,
            String planId,
            String role,
            BindingResolution resolution,
            String actionType
    ) {
        entities.put(BINDING_ROLE, role);
        CandidateBindingResult binding = resolution.binding();
        Map<String, ActionPlanBinding> bindings = new LinkedHashMap<>();
        bindings.put(role, new ActionPlanBinding(
                role,
                SOURCE_FOLDER_ROLE.equals(role) ? "source_folder" : "target_folder",
                "multiple_candidates",
                binding.query(),
                null,
                binding.candidates(),
                binding.candidates().size(),
                Map.of("bindingRole", role)
        ));
        String roleLabel = SOURCE_FOLDER_ROLE.equals(role) ? "源目录" : "目标目录";
        ActionPlan plan = blockedPlan(planId, actionType, "candidate_selection_required", bindings,
                "找到了多个同名" + roleLabel + "，请根据完整路径选择。");
        return response.withPlanningState(
                Map.copyOf(entities),
                binding,
                plan,
                "wait_for_candidate_selection",
                "找到了多个同名" + roleLabel + "，请根据完整路径选择正确的一项。"
        );
    }

    private IntentRecognitionResponse bindingFailed(
            IntentRecognitionResponse response,
            Map<String, Object> entities,
            String planId,
            String role,
            BindingResolution resolution,
            String actionType
    ) {
        ActionPlan plan = blockedPlan(planId, actionType, "binding_required", Map.of(), resolution.message());
        CandidateBindingResult binding = resolution.binding() == null
                ? CandidateBindingResult.skipped("no_candidates", resolution.message())
                : resolution.binding();
        return response.withPlanningState(
                Map.copyOf(entities),
                binding,
                plan,
                "ask_clarification",
                resolution.message()
        );
    }

    private IntentRecognitionResponse collectionUnavailable(
            IntentRecognitionResponse response,
            Map<String, Object> entities,
            String planId,
            String actionType,
            String message
    ) {
        String safeMessage = message == null || message.isBlank()
                ? "暂时无法得到完整且可执行的文件集合，请缩小范围或稍后重试。"
                : message;
        return response.withPlanningState(
                Map.copyOf(entities),
                CandidateBindingResult.skipped("collection_not_executable", safeMessage),
                blockedPlan(planId, actionType, "binding_required", Map.of(), safeMessage),
                "ask_clarification",
                safeMessage
        );
    }

    private ActionPlan readyPlan(
            String planId,
            String actionType,
            Map<String, Object> entities,
            List<CandidateItem> sourceCandidates,
            Map<String, Object> sourceFilter,
            CandidateItem sourceFolder,
            CandidateItem targetParent
    ) {
        Map<String, ActionPlanBinding> bindings = new LinkedHashMap<>();
        if (sourceFolder != null) {
            bindings.put(SOURCE_FOLDER_ROLE, resolvedFolderBinding(SOURCE_FOLDER_ROLE, "source_folder", sourceFolder));
        }
        bindings.put("sourceCollection", new ActionPlanBinding(
                "sourceCollection",
                "source_collection",
                "resolved",
                sourceDescription(entities),
                null,
                sourceCandidates.stream().limit(previewItems).toList(),
                sourceCandidates.size(),
                Map.copyOf(sourceFilter)
        ));
        if ("collection.move".equals(actionType)) {
            bindings.put(TARGET_PARENT_ROLE, resolvedFolderBinding(TARGET_PARENT_ROLE, "target_folder", targetParent));
        }
        String stepAction = "collection.move".equals(actionType) ? "node.batch_move" : "node.batch_trash";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("snapshotCount", sourceCandidates.size());
        if ("collection.move".equals(actionType)) {
            params.put("parentId", targetParent == null ? null : targetParent.nodeId());
        }
        ActionPlanStep step = new ActionPlanStep(
                "collection.move".equals(actionType) ? "move_collection" : "trash_collection",
                stepAction,
                "pending",
                params,
                List.of(),
                List.of(),
                "collectionResult"
        );
        String summary = "collection.move".equals(actionType)
                ? "将 " + sourceCandidates.size() + " 个项目移动到 " + displayPath(targetParent) + "。"
                : "将 " + sourceCandidates.size() + " 个项目移入回收站。";
        return new ActionPlan(
                "action_plan_v2",
                planId,
                "collection_review_required",
                "collection",
                actionType,
                "collection.move".equals(actionType) ? "medium" : "high",
                "collection_then_final_review",
                "zh-CN",
                Map.copyOf(bindings),
                List.of(step),
                List.of(),
                summary,
                List.of(new ActionPlanMessage("info", "collection_snapshot_ready", "完整集合已在服务端形成安全快照。"))
        );
    }

    private ActionPlan blockedPlan(
            String planId,
            String actionType,
            String status,
            Map<String, ActionPlanBinding> bindings,
            String message
    ) {
        return new ActionPlan(
                "action_plan_v2",
                planId,
                status,
                "collection",
                actionType,
                "collection.trash".equals(actionType) ? "high" : "medium",
                "collection_then_final_review",
                "zh-CN",
                bindings,
                List.of(),
                List.of(),
                message,
                List.of(new ActionPlanMessage("warning", status, message))
        );
    }

    private ActionPlanBinding resolvedFolderBinding(String key, String kind, CandidateItem candidate) {
        return new ActionPlanBinding(
                key,
                kind,
                "resolved",
                candidate == null ? "" : candidate.name(),
                candidate,
                candidate == null ? List.of() : List.of(candidate),
                candidate == null ? 0 : 1,
                candidate != null && candidate.nodeId() == null ? Map.of("root", true) : Map.of()
        );
    }

    private CandidateItem contextFolder(AssistantConversationState conversation) {
        if (conversation != null && conversation.focus() != null) {
            CandidateItem focused = conversation.focus().effectiveCandidate();
            if (focused != null && "FOLDER".equalsIgnoreCase(focused.type())) {
                return focused;
            }
        }
        List<CandidateItem> previous = previousCandidates(conversation, "ANY");
        if (previous.isEmpty()) {
            return null;
        }
        List<Long> parentIds = previous.stream().map(CandidateItem::parentId).distinct().toList();
        if (parentIds.size() != 1) {
            return null;
        }
        Long parentId = parentIds.getFirst();
        if (parentId == null) {
            return rootCandidate();
        }
        String parentPath = parentPath(previous.getFirst());
        String parentName = parentPath.equals("/")
                ? "根目录"
                : parentPath.substring(parentPath.lastIndexOf('/') + 1);
        return new CandidateItem(parentId, null, parentName, "FOLDER", 0L, "", "", "", parentPath, List.of());
    }

    private List<CandidateItem> previousCandidates(AssistantConversationState conversation, String nodeType) {
        if (conversation == null || conversation.focus() == null || conversation.focus().candidateBinding() == null) {
            return List.of();
        }
        return conversation.focus().candidateBinding().candidates().stream()
                .filter(candidate -> "ANY".equals(nodeType) || nodeType.equalsIgnoreCase(candidate.type()))
                .toList();
    }

    private void rememberResolvedFolder(Map<String, Object> entities, CandidateItem candidate, boolean source) {
        if (candidate == null) {
            return;
        }
        String prefix = source ? "source" : "target";
        putId(entities, prefix + "_parent_id", candidate.nodeId());
        entities.put(prefix + "_folder_path", displayPath(candidate));
        if (candidate.nodeId() == null) {
            entities.put(prefix + "_is_root", true);
        }
    }

    private CandidateItem storedFolder(Map<String, Object> entities, boolean source) {
        String prefix = source ? "source" : "target";
        boolean root = booleanValue(entities.get(prefix + "_is_root"));
        Long nodeId = longValue(entities.get(prefix + "_parent_id"));
        if (nodeId == null && !root) {
            return null;
        }
        if (root) {
            return rootCandidate();
        }
        String path = stringValue(entities.get(prefix + "_folder_path"));
        String name = stringValue(entities.get(prefix + "_folder"));
        if (name.isBlank() && !path.isBlank()) {
            int separator = path.lastIndexOf('/');
            name = separator >= 0 ? path.substring(separator + 1) : path;
        }
        return new CandidateItem(nodeId, null, name, "FOLDER", 0L, "", "", "", path, List.of());
    }

    private Map<String, Object> sourceFilter(Map<String, Object> entities) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("selectorVersion", CollectionOperationSelectorResolver.SELECTOR_VERSION);
        filter.put("directChildren", true);
        filter.put("recursive", false);
        filter.put("nodeType", stringValue(entities.get("source_node_type")));
        String sourceKind = stringValue(entities.get("source_kind"));
        if (CollectionOperationSelectorResolver.SOURCE_PREVIOUS_RESULTS.equals(sourceKind)) {
            filter.put("sourceReference", "previousResults");
        } else {
            Long sourceParentId = longValue(entities.get("source_parent_id"));
            filter.put("parentId", sourceParentId == null ? "" : sourceParentId);
            filter.put("root", booleanValue(entities.get("source_is_root")));
            filter.put("includeFolders", !"FILE".equals(stringValue(entities.get("source_node_type"))));
        }
        return filter;
    }

    private void putId(Map<String, Object> entities, String key, Long value) {
        if (value == null) {
            entities.remove(key);
        } else {
            entities.put(key, value);
        }
    }

    private boolean sameParent(List<CandidateItem> candidates, CandidateItem targetParent) {
        Long targetId = targetParent == null ? null : targetParent.nodeId();
        return candidates.stream().allMatch(candidate -> java.util.Objects.equals(candidate.parentId(), targetId));
    }

    private boolean isScopedCollection(IntentRecognitionResponse response) {
        return response != null
                && response.actionDraft() != null
                && List.of("collection.move", "collection.trash").contains(response.actionDraft().type());
    }

    private boolean isRootAlias(String value) {
        return ROOT_ALIASES.contains(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
    }

    private CandidateItem rootCandidate() {
        return new CandidateItem(null, null, "根目录", "FOLDER", 0L, "", "", "", "/", List.of());
    }

    private String sourceDescription(Map<String, Object> entities) {
        String sourceKind = stringValue(entities.get("source_kind"));
        if (CollectionOperationSelectorResolver.SOURCE_PREVIOUS_RESULTS.equals(sourceKind)) {
            return "上一轮结果";
        }
        String path = stringValue(entities.get("source_folder_path"));
        return path.isBlank() ? stringValue(entities.get("source_folder")) : path;
    }

    private String displayPath(CandidateItem candidate) {
        if (candidate == null) {
            return "";
        }
        return candidate.path() == null || candidate.path().isBlank() ? candidate.name() : candidate.path();
    }

    private String parentPath(CandidateItem candidate) {
        String path = candidate.path() == null ? "" : candidate.path();
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null || String.valueOf(value).isBlank() ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private record BindingResolution(
            boolean required,
            CandidateItem candidate,
            CandidateBindingResult binding,
            String message
    ) {
        private static BindingResolution notRequired() {
            return new BindingResolution(false, null, null, "");
        }

        private static BindingResolution resolved(CandidateItem candidate) {
            return new BindingResolution(true, candidate, null, "");
        }

        private static BindingResolution selection(CandidateBindingResult binding) {
            return new BindingResolution(true, null, binding, binding.message());
        }

        private static BindingResolution failed(String message) {
            return new BindingResolution(true, null, null, message);
        }

        private static BindingResolution failed(String message, CandidateBindingResult binding) {
            return new BindingResolution(true, null, binding, message);
        }

        private boolean needsSelection() {
            return binding != null && binding.candidates().size() > 1;
        }

        private boolean failed() {
            return required && candidate == null && !needsSelection();
        }
    }
}
