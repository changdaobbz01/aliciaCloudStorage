package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ActionPlanService {

    private static final String VERSION = "action_plan_v2";

    private final String defaultLocale;
    private final JsonNode compositeDefinitions;
    private final JsonNode collectionDefinitions;

    public ActionPlanService(RagConfigLoader configLoader) {
        JsonNode policies = configLoader.loadJson("rag/conversation/policies.json");
        this.defaultLocale = policies.path("defaultLocale").asText("zh-CN");
        this.compositeDefinitions = configLoader.loadJson("rag/conversation/composite_actions.json").path("composites");
        this.collectionDefinitions = configLoader.loadJson("rag/conversation/collection_actions.json").path("collections");
    }

    public ActionPlan build(IntentRecognitionResponse response) {
        return build(response, AssistantClientContext.empty());
    }

    public ActionPlan build(IntentRecognitionResponse response, AssistantClientContext clientContext) {
        if (response == null) {
            return ActionPlan.skipped("understanding", "ActionPlan 尚未生成。");
        }

        AssistantClientContext safeClientContext = clientContext == null
                ? AssistantClientContext.empty()
                : clientContext;

        String rawActionType = response.actionDraft() == null ? "none" : response.actionDraft().type();
        if (rawActionType == null || rawActionType.isBlank() || "none".equals(rawActionType)) {
            return ActionPlan.skipped("completed", response.assistantText());
        }
        if (rawActionType.startsWith("composite.")) {
            return buildCompositePlan(response, rawActionType, safeClientContext);
        }
        if (rawActionType.startsWith("collection.")) {
            return buildCollectionPlan(response, rawActionType, safeClientContext);
        }
        String canonicalAction = canonicalActionType(rawActionType);
        String status = planStatus(response);
        String risk = response.safety() == null ? "none" : response.safety().risk();
        CandidateItem selectedCandidate = selectedCandidate(response.candidateBinding());
        Map<String, ActionPlanBinding> bindings = bindingsFor(response, rawActionType, selectedCandidate);
        List<ActionPlanStep> steps = stepsFor(
                response,
                rawActionType,
                canonicalAction,
                selectedCandidate,
                status,
                safeClientContext
        );
        List<String> requiredClientFields = steps.stream()
                .flatMap(step -> step.requiredClientFields().stream())
                .distinct()
                .toList();

        return new ActionPlan(
                VERSION,
                "ap_" + response.id(),
                status,
                "atomic",
                canonicalAction,
                risk,
                confirmationLevel(rawActionType, risk),
                defaultLocale,
                bindings,
                steps,
                requiredClientFields,
                summaryFor(response, canonicalAction, selectedCandidate, status),
                List.of(new ActionPlanMessage("info", status, response.assistantText()))
        );
    }

    public ActionPlan withBackendActionDraft(ActionPlan plan, BackendActionDraft draft) {
        if (plan == null || draft == null || !"collection".equals(plan.planKind())) {
            return plan;
        }

        String status = switch (draft.status()) {
            case "backend_action_ready" -> "ready_to_execute";
            case "client_action_required" -> "client_input_required";
            case "not_confirmed", "not_requested" -> plan.status();
            default -> "binding_required";
        };
        List<ActionPlanStep> steps = plan.steps().stream()
                .map(step -> new ActionPlanStep(
                        step.stepId(),
                        step.action(),
                        "ready_to_execute".equals(status) ? "ready" : step.status(),
                        step.params(),
                        step.dependsOn(),
                        step.requiredClientFields(),
                        step.outputKey()
                ))
                .toList();
        List<ActionPlanMessage> messages = new ArrayList<>(plan.messages());
        if (draft.message() != null && !draft.message().isBlank()) {
            messages.add(new ActionPlanMessage(
                    "ready_to_execute".equals(status) ? "info" : "warning",
                    draft.status(),
                    draft.message()
            ));
        }

        return new ActionPlan(
                plan.version(),
                plan.planId(),
                status,
                plan.planKind(),
                plan.actionType(),
                plan.risk(),
                plan.confirmationLevel(),
                plan.locale(),
                plan.bindings(),
                steps,
                plan.requiredClientFields(),
                "ready_to_execute".equals(status) ? plan.summary() + " 已生成后端执行草稿。" : plan.summary(),
                List.copyOf(messages)
        );
    }

    private ActionPlan buildCompositePlan(
            IntentRecognitionResponse response,
            String actionType,
            AssistantClientContext clientContext
    ) {
        JsonNode definition = compositeDefinitions.path(actionType);
        if (definition.isMissingNode() || !definition.path("enabled").asBoolean(false)) {
            return unsupportedConfiguredPlan(response, actionType, "composite");
        }

        CandidateItem selectedCandidate = selectedCandidate(response.candidateBinding());
        String status = compositeStatus(response, definition, selectedCandidate);
        Map<String, ActionPlanBinding> bindings = configuredBindings(
                response,
                definition.path("bindings"),
                selectedCandidate,
                Map.of(),
                clientContext
        );
        List<ActionPlanStep> steps = configuredSteps(
                definition.path("steps"),
                response,
                selectedCandidate,
                Map.of(),
                status,
                clientContext
        );
        List<String> requiredClientFields = requiredClientFields(steps);

        return new ActionPlan(
                VERSION,
                "ap_" + response.id(),
                status,
                "composite",
                actionType,
                text(definition, "risk", response.safety() == null ? "medium" : response.safety().risk()),
                text(definition, "confirmationLevel", "candidate_then_final_review"),
                defaultLocale,
                bindings,
                steps,
                requiredClientFields,
                compositeSummary(response, actionType, selectedCandidate, status),
                List.of(new ActionPlanMessage("info", status, response.assistantText()))
        );
    }

    private ActionPlan buildCollectionPlan(
            IntentRecognitionResponse response,
            String actionType,
            AssistantClientContext clientContext
    ) {
        JsonNode definition = collectionDefinitions.path(actionType);
        if (definition.isMissingNode() || !definition.path("enabled").asBoolean(false)) {
            return unsupportedConfiguredPlan(response, actionType, "collection");
        }

        CandidateItem selectedCandidate = selectedCandidate(response.candidateBinding());
        Map<String, Object> sourceFilter = renderObject(definition.path("sourceCollection").path("filter"), response, selectedCandidate, Map.of());
        String status = collectionStatus(response, definition, selectedCandidate);
        Map<String, ActionPlanBinding> bindings = new LinkedHashMap<>();
        bindings.put(sourceCollectionKey(definition), new ActionPlanBinding(
                sourceCollectionKey(definition),
                "source_collection",
                "unresolved",
                "",
                null,
                List.of(),
                null,
                sourceFilter
        ));
        bindings.putAll(configuredBindings(
                response,
                definition.path("bindings"),
                selectedCandidate,
                sourceFilter,
                clientContext
        ));

        List<ActionPlanStep> steps = configuredSteps(
                definition.path("steps"),
                response,
                selectedCandidate,
                sourceFilter,
                status,
                clientContext
        );
        List<String> requiredClientFields = requiredClientFields(steps);

        return new ActionPlan(
                VERSION,
                "ap_" + response.id(),
                status,
                "collection",
                actionType,
                text(definition, "risk", response.safety() == null ? "medium" : response.safety().risk()),
                text(definition, "confirmationLevel", "collection_then_final_review"),
                defaultLocale,
                bindings,
                steps,
                requiredClientFields,
                collectionSummary(actionType, sourceFilter, selectedCandidate, status),
                List.of(new ActionPlanMessage("info", status, response.assistantText()))
        );
    }

    private ActionPlan unsupportedConfiguredPlan(IntentRecognitionResponse response, String actionType, String planKind) {
        return new ActionPlan(
                VERSION,
                "ap_" + response.id(),
                "binding_required",
                planKind,
                actionType,
                response.safety() == null ? "none" : response.safety().risk(),
                "final_review",
                defaultLocale,
                Map.of(),
                List.of(),
                List.of(),
                "当前动作尚未配置 ActionPlan 模板。",
                List.of(new ActionPlanMessage("warning", "unsupported_action_plan", "当前动作尚未配置 ActionPlan 模板。"))
        );
    }

    private String planStatus(IntentRecognitionResponse response) {
        if (response.missingSlots() != null && !response.missingSlots().isEmpty()) {
            return "clarification_required";
        }
        if ("ask_clarification".equals(response.nextAction())) {
            return "clarification_required";
        }

        CandidateBindingResult binding = response.candidateBinding();
        if (binding != null) {
            return switch (binding.status()) {
                case "multiple_candidates", "candidate_selection_out_of_range" -> "candidate_selection_required";
                case "no_candidates", "missing_query", "storage_api_not_configured", "missing_authorization",
                     "storage_api_error" -> "binding_required";
                default -> statusFromBackendDraftOrNextAction(response);
            };
        }

        return statusFromBackendDraftOrNextAction(response);
    }

    private String statusFromBackendDraftOrNextAction(IntentRecognitionResponse response) {
        BackendActionDraft backendActionDraft = response.backendActionDraft();
        if (backendActionDraft != null) {
            return switch (backendActionDraft.status()) {
                case "backend_action_ready" -> "ready_to_execute";
                case "client_action_required" -> "client_input_required";
                case "missing_candidate_fields", "missing_target_candidate", "missing_required_entities" -> "binding_required";
                default -> statusFromNextAction(response);
            };
        }
        return statusFromNextAction(response);
    }

    private String statusFromNextAction(IntentRecognitionResponse response) {
        return switch (response.nextAction()) {
            case "show_search_results" -> "completed";
            case "wait_for_candidate_selection" -> "candidate_selection_required";
            case "wait_for_user_confirmation" -> "review_required";
            case "handoff_to_backend" -> "ready_to_execute";
            case "handoff_to_client_upload" -> "client_input_required";
            case "wait_for_backend_binding" -> "binding_required";
            case "respond_only" -> "completed";
            default -> "understanding";
        };
    }

    private Map<String, ActionPlanBinding> bindingsFor(
            IntentRecognitionResponse response,
            String rawActionType,
            CandidateItem selectedCandidate
    ) {
        CandidateBindingResult candidateBinding = response.candidateBinding();
        if (candidateBinding == null || candidateBinding.candidates().isEmpty()) {
            return Map.of();
        }

        String key = bindingKey(rawActionType);
        Map<String, ActionPlanBinding> bindings = new LinkedHashMap<>();
        bindings.put(key, new ActionPlanBinding(
                key,
                bindingKind(rawActionType),
                bindingStatus(candidateBinding.status()),
                candidateBinding.query(),
                selectedCandidate,
                candidateBinding.candidates(),
                candidateBinding.candidates().size(),
                Map.of()
        ));
        return bindings;
    }

    private List<ActionPlanStep> stepsFor(
            IntentRecognitionResponse response,
            String rawActionType,
            String canonicalAction,
            CandidateItem selectedCandidate,
            String status,
            AssistantClientContext clientContext
    ) {
        if (selectedCandidate == null) {
            return List.of();
        }
        if (selectedCandidate.nodeId() == null && !"upload_target".equals(rawActionType)) {
            return List.of();
        }

        Map<String, Object> params = switch (rawActionType) {
            case "rename" -> renameParams(response, selectedCandidate);
            case "delete" -> Map.of("nodeId", selectedCandidate.nodeId());
            case "share" -> shareParams(response, selectedCandidate);
            case "upload_target" -> {
                Map<String, Object> uploadParams = new LinkedHashMap<>();
                uploadParams.put("parentId", selectedCandidate.nodeId());
                yield Collections.unmodifiableMap(uploadParams);
            }
            default -> Map.of();
        };

        if (params.isEmpty() && !"upload_target".equals(rawActionType)) {
            return List.of();
        }

        List<String> requiredClientFields = remainingClientFields(
                "upload_target".equals(rawActionType) ? List.of("files") : List.of(),
                clientContext
        );
        return List.of(new ActionPlanStep(
                stepId(canonicalAction),
                canonicalAction,
                stepStatus(status, requiredClientFields),
                params,
                List.of(),
                requiredClientFields,
                outputKey(canonicalAction)
        ));
    }

    private Map<String, Object> renameParams(IntentRecognitionResponse response, CandidateItem selectedCandidate) {
        Object newName = response.entities().get("new_name");
        if (newName == null || String.valueOf(newName).isBlank()) {
            return Map.of();
        }
        return Map.of(
                "nodeId", selectedCandidate.nodeId(),
                "name", String.valueOf(newName)
        );
    }

    private Map<String, Object> shareParams(IntentRecognitionResponse response, CandidateItem selectedCandidate) {
        BackendActionDraft draft = response.backendActionDraft();
        if (draft != null && draft.body() != null && !draft.body().isEmpty()) {
            return draft.body();
        }
        return Map.of(
                "nodeIds", List.of(selectedCandidate.nodeId()),
                "title", selectedCandidate.name(),
                "allowDownload", true,
                "allowSave", true
        );
    }

    private Map<String, ActionPlanBinding> configuredBindings(
            IntentRecognitionResponse response,
            JsonNode bindingDefinitions,
            CandidateItem selectedCandidate,
            Map<String, Object> sourceFilter,
            AssistantClientContext clientContext
    ) {
        if (!bindingDefinitions.isArray()) {
            return Map.of();
        }

        Map<String, ActionPlanBinding> result = new LinkedHashMap<>();
        for (JsonNode binding : bindingDefinitions) {
            String key = binding.path("key").asText("");
            String kind = binding.path("kind").asText("");
            if (key.isBlank()) {
                continue;
            }
            if ("client_files".equals(kind)) {
                int availableCount = clientContext.availableClientInputs().getOrDefault("files", 0);
                result.put(key, new ActionPlanBinding(
                        key,
                        kind,
                        availableCount > 0 ? "resolved" : "unresolved",
                        "",
                        null,
                        List.of(),
                        availableCount > 0 ? availableCount : null,
                        Map.of("providedBy", binding.path("providedBy").asText("client"))
                ));
                continue;
            }

            CandidateBindingResult candidateBinding = response.candidateBinding();
            Map<String, Object> bindingFilter = Map.of();
            if ("source_collection".equals(kind) && sourceFilter != null) {
                bindingFilter = sourceFilter;
            }
            result.put(key, new ActionPlanBinding(
                    key,
                    kind,
                    candidateBinding == null ? "unresolved" : bindingStatus(candidateBinding.status()),
                    candidateBinding == null ? "" : candidateBinding.query(),
                    selectedCandidate,
                    candidateBinding == null ? List.of() : candidateBinding.candidates(),
                    candidateBinding == null ? null : candidateBinding.candidates().size(),
                    bindingFilter
            ));
        }
        return result;
    }

    private List<ActionPlanStep> configuredSteps(
            JsonNode stepDefinitions,
            IntentRecognitionResponse response,
            CandidateItem selectedCandidate,
            Map<String, Object> sourceFilter,
            String planStatus,
            AssistantClientContext clientContext
    ) {
        if (!stepDefinitions.isArray()) {
            return List.of();
        }

        List<ActionPlanStep> result = new ArrayList<>();
        for (JsonNode step : stepDefinitions) {
            List<String> requiredClientFields = remainingClientFields(
                    stringList(step.path("requiredClientFields")),
                    clientContext
            );
            result.add(new ActionPlanStep(
                    step.path("stepId").asText(""),
                    step.path("action").asText(""),
                    configuredStepStatus(planStatus, requiredClientFields),
                    renderObject(step.path("params"), response, selectedCandidate, sourceFilter),
                    stringList(step.path("dependsOn")),
                    requiredClientFields,
                    step.path("outputKey").asText("")
            ));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> renderObject(
            JsonNode node,
            IntentRecognitionResponse response,
            CandidateItem selectedCandidate,
            Map<String, Object> sourceFilter
    ) {
        if (!node.isObject()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        node.properties().forEach(entry -> result.put(
                entry.getKey(),
                renderValue(entry.getValue(), response, selectedCandidate, sourceFilter)
        ));
        return Collections.unmodifiableMap(result);
    }

    private Object renderValue(
            JsonNode node,
            IntentRecognitionResponse response,
            CandidateItem selectedCandidate,
            Map<String, Object> sourceFilter
    ) {
        if (node.isObject()) {
            return renderObject(node, response, selectedCandidate, sourceFilter);
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(item -> values.add(renderValue(item, response, selectedCandidate, sourceFilter)));
            return List.copyOf(values);
        }
        if (node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        return resolveExpression(node.asText(""), response, selectedCandidate, sourceFilter);
    }

    private Object resolveExpression(
            String expression,
            IntentRecognitionResponse response,
            CandidateItem selectedCandidate,
            Map<String, Object> sourceFilter
    ) {
        if (!expression.startsWith("$")) {
            return expression;
        }
        if ("$entities.scope_or_default".equals(expression)) {
            Object scope = response.entities().get("scope");
            return scope == null || String.valueOf(scope).isBlank() ? "all_drive" : scope;
        }
        if ("$entities.include_folders_for_result_type".equals(expression)) {
            Object resultType = response.entities().get("result_type");
            return resultType == null || !"FILE".equalsIgnoreCase(String.valueOf(resultType));
        }
        if (expression.startsWith("$entities.")) {
            String key = expression.substring("$entities.".length());
            Object value = response.entities().get(key);
            return value == null ? "" : value;
        }
        if ("$bindings.targetParent.nodeId".equals(expression)) {
            return selectedCandidate == null ? expression : selectedCandidate.nodeId();
        }
        if ("$bindings.targetParent.path".equals(expression)) {
            return selectedCandidate == null ? expression : selectedCandidate.path();
        }
        if ("$bindings.sourceCollection.filter".equals(expression)) {
            return sourceFilter;
        }
        if ("$bindings.sourceCollection.nodeIds".equals(expression)) {
            return expression;
        }
        if (expression.startsWith("$steps.")) {
            return expression;
        }
        return expression;
    }

    private String compositeStatus(
            IntentRecognitionResponse response,
            JsonNode definition,
            CandidateItem selectedCandidate
    ) {
        String basicStatus = blockingStatus(response, true, selectedCandidate);
        if (!basicStatus.isBlank()) {
            return basicStatus;
        }
        if (isConfirmedUnsupportedActionPlan(response)) {
            return compositeRequiresClientInput(definition) ? "client_input_required" : "ready_to_execute";
        }
        return "review_required";
    }

    private String collectionStatus(
            IntentRecognitionResponse response,
            JsonNode definition,
            CandidateItem selectedCandidate
    ) {
        String basicStatus = blockingStatus(response, hasTargetBinding(definition), selectedCandidate);
        if (!basicStatus.isBlank()) {
            return basicStatus;
        }
        return "collection_review_required";
    }

    private String blockingStatus(
            IntentRecognitionResponse response,
            boolean requiresTargetBinding,
            CandidateItem selectedCandidate
    ) {
        if (response.missingSlots() != null && !response.missingSlots().isEmpty()) {
            return "clarification_required";
        }
        if ("ask_clarification".equals(response.nextAction())) {
            return "clarification_required";
        }

        CandidateBindingResult binding = response.candidateBinding();
        if (binding != null) {
            if ("multiple_candidates".equals(binding.status()) || "candidate_selection_out_of_range".equals(binding.status())) {
                return "candidate_selection_required";
            }
            if (requiresTargetBinding && isBindingUnavailable(binding.status())) {
                return "binding_required";
            }
        }

        if (requiresTargetBinding && selectedCandidate == null) {
            return "binding_required";
        }
        return "";
    }

    private boolean isBindingUnavailable(String status) {
        return List.of(
                "no_candidates",
                "missing_query",
                "storage_api_not_configured",
                "missing_authorization",
                "storage_api_error"
        ).contains(status);
    }

    private boolean isConfirmedUnsupportedActionPlan(IntentRecognitionResponse response) {
        BackendActionDraft draft = response.backendActionDraft();
        return draft != null && "unsupported_action".equals(draft.status());
    }

    private boolean compositeRequiresClientInput(JsonNode definition) {
        for (JsonNode step : definition.path("steps")) {
            if (!stringList(step.path("requiredClientFields")).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTargetBinding(JsonNode definition) {
        for (JsonNode binding : definition.path("bindings")) {
            if ("target_folder".equals(binding.path("kind").asText("")) || "folder".equals(binding.path("kind").asText(""))) {
                return true;
            }
        }
        return false;
    }

    private List<String> requiredClientFields(List<ActionPlanStep> steps) {
        return steps.stream()
                .flatMap(step -> step.requiredClientFields().stream())
                .distinct()
                .toList();
    }

    private List<String> remainingClientFields(
            List<String> requiredClientFields,
            AssistantClientContext clientContext
    ) {
        AssistantClientContext safeClientContext = clientContext == null
                ? AssistantClientContext.empty()
                : clientContext;
        return requiredClientFields.stream()
                .filter(field -> !safeClientContext.provides(field))
                .distinct()
                .toList();
    }

    private String configuredStepStatus(String planStatus, List<String> requiredClientFields) {
        if ("ready_to_execute".equals(planStatus)) {
            return "ready";
        }
        if ("client_input_required".equals(planStatus)) {
            return requiredClientFields.isEmpty() ? "ready" : "blocked";
        }
        return "pending";
    }

    private String sourceCollectionKey(JsonNode definition) {
        String key = definition.path("sourceCollection").path("key").asText("");
        return key.isBlank() ? "sourceCollection" : key;
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        });
        return List.copyOf(values);
    }

    private CandidateItem selectedCandidate(CandidateBindingResult binding) {
        if (binding == null || binding.candidates().isEmpty()) {
            return null;
        }
        if (binding.selectedCandidate() != null) {
            return binding.selectedCandidate();
        }
        return binding.candidates().size() == 1 ? binding.candidates().getFirst() : null;
    }

    private String canonicalActionType(String actionType) {
        return switch (actionType == null ? "" : actionType) {
            case "rename" -> "node.rename";
            case "delete" -> "node.trash";
            case "share" -> "share.create";
            case "upload_target" -> "file.upload";
            case "search" -> "search";
            default -> "none";
        };
    }

    private String bindingKey(String actionType) {
        return switch (actionType == null ? "" : actionType) {
            case "upload_target" -> "targetParent";
            case "search" -> "searchResults";
            default -> "targetNode";
        };
    }

    private String bindingKind(String actionType) {
        return switch (actionType == null ? "" : actionType) {
            case "upload_target" -> "target_folder";
            case "search" -> "source_collection";
            default -> "node";
        };
    }

    private String bindingStatus(String status) {
        return switch (status == null ? "" : status) {
            case "multiple_candidates" -> "multiple_candidates";
            case "single_candidate" -> "single_candidate";
            case "selected_candidate" -> "selected_candidate";
            case "candidate_selection_out_of_range" -> "multiple_candidates";
            case "no_candidates" -> "no_candidates";
            case "search_results_ready" -> "resolved";
            default -> "unresolved";
        };
    }

    private String confirmationLevel(String actionType, String risk) {
        if ("none".equals(actionType) || "search".equals(actionType)) {
            return "none";
        }
        if ("high".equals(risk) || "critical".equals(risk)) {
            return "candidate_then_final_review";
        }
        if ("upload_target".equals(actionType)) {
            return "final_review";
        }
        return "candidate_then_final_review";
    }

    private String stepStatus(String planStatus, List<String> requiredClientFields) {
        return switch (planStatus) {
            case "ready_to_execute" -> "ready";
            case "client_input_required" -> requiredClientFields.isEmpty() ? "ready" : "blocked";
            default -> "pending";
        };
    }

    private String stepId(String canonicalAction) {
        return canonicalAction.replace('.', '_');
    }

    private String outputKey(String canonicalAction) {
        return switch (canonicalAction) {
            case "node.rename" -> "renamedNode";
            case "node.trash" -> "trashedNode";
            case "share.create" -> "share";
            case "file.upload" -> "uploadedFiles";
            default -> "";
        };
    }

    private String compositeSummary(
            IntentRecognitionResponse response,
            String actionType,
            CandidateItem selectedCandidate,
            String status
    ) {
        if ("clarification_required".equals(status)) {
            return "需要补充信息：" + String.join("、", response.missingSlots());
        }
        if ("candidate_selection_required".equals(status)) {
            return "需要先选择目标目录。";
        }
        if ("composite.create_folder_then_upload".equals(actionType)) {
            Object folderName = response.entities().get("new_folder_name");
            String parentPath = selectedCandidate == null ? "目标目录" : selectedCandidate.path();
            return "将在 " + parentPath + " 下新建文件夹 " + folderName + "，然后上传客户端选择的文件。";
        }
        return response.assistantText();
    }

    private String collectionSummary(
            String actionType,
            Map<String, Object> sourceFilter,
            CandidateItem selectedCandidate,
            String status
    ) {
        if ("candidate_selection_required".equals(status)) {
            return "需要先选择目标目录。";
        }
        String filterText = sourceFilter.isEmpty() ? "集合筛选条件" : sourceFilter.toString();
        return switch (actionType) {
            case "collection.trash_by_name_contains", "collection.trash_by_category" ->
                    "将按 " + filterText + " 生成批量删除预览，确认后默认移入回收站。";
            case "collection.move_by_extension", "collection.move_by_name_contains" -> {
                String targetPath = selectedCandidate == null ? "目标目录" : selectedCandidate.path();
                yield "将按 " + filterText + " 生成批量移动预览，目标目录：" + targetPath + "。";
            }
            default -> "将生成集合动作预览。";
        };
    }

    private String summaryFor(
            IntentRecognitionResponse response,
            String canonicalAction,
            CandidateItem selectedCandidate,
            String status
    ) {
        if ("clarification_required".equals(status)) {
            return "需要补充信息：" + String.join("、", response.missingSlots());
        }
        if ("candidate_selection_required".equals(status)) {
            return "需要先选择真实候选项。";
        }
        if (selectedCandidate == null) {
            return response.assistantText();
        }
        return switch (canonicalAction) {
            case "node.rename" -> "将 " + selectedCandidate.path() + " 重命名为 " + response.entities().get("new_name");
            case "node.trash" -> "将 " + selectedCandidate.path() + " 移入回收站";
            case "share.create" -> "为 " + selectedCandidate.path() + " 创建分享链接";
            case "file.upload" -> "上传文件到 " + selectedCandidate.path();
            case "search" -> "展示匹配到的搜索结果";
            default -> response.assistantText();
        };
    }
}
