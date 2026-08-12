package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BackendActionDraftService {

    private final String bridgeVersion;
    private final Map<String, ActionBridgeDefinition> actionsByType;

    public BackendActionDraftService(RagConfigLoader configLoader) {
        JsonNode config = configLoader.loadJson("rag/conversation/action_bridge.json");
        this.bridgeVersion = config.path("version").asText("action_bridge_v1");
        this.actionsByType = loadActions(config.path("actions"));
    }

    public BackendActionDraft build(
            IntentRecognitionResponse response,
            CandidateBindingResult candidateBinding,
            boolean confirmedByUser
    ) {
        return build(response, candidateBinding, confirmedByUser, AssistantClientContext.empty());
    }

    public BackendActionDraft build(
            IntentRecognitionResponse response,
            CandidateBindingResult candidateBinding,
            boolean confirmedByUser,
            AssistantClientContext clientContext
    ) {
        if (!confirmedByUser) {
            return BackendActionDraft.skipped("not_confirmed", "用户尚未确认，暂不生成后端请求草稿。");
        }
        if (response == null || response.actionDraft() == null) {
            return BackendActionDraft.skipped("not_requested", "没有可生成执行草稿的动作。");
        }
        if (!response.missingSlots().isEmpty()) {
            return BackendActionDraft.skipped("waiting_for_clarification", "仍有缺失信息，暂不生成后端请求草稿。");
        }

        String actionType = response.actionDraft().type();
        ActionBridgeDefinition definition = actionsByType.get(actionType);
        if (definition == null || !definition.enabled()) {
            return BackendActionDraft.skipped("unsupported_action", "当前动作未配置后端执行桥接契约。");
        }
        if (actionType.startsWith("collection.")) {
            return buildCollectionDraft(response, definition);
        }

        CandidateItem candidate = selectedCandidate(candidateBinding);
        if (candidate == null) {
            return BackendActionDraft.skipped("missing_target_candidate", "尚未锁定真实候选，不能生成后端请求草稿。");
        }

        List<String> missingCandidateFields = missingCandidateFields(definition.requiredCandidateFields(), candidate);
        if (!missingCandidateFields.isEmpty()) {
            return BackendActionDraft.skipped(
                    "missing_candidate_fields",
                    "候选缺少后端执行所需字段：" + String.join("、", missingCandidateFields)
            );
        }

        List<String> missingEntitySlots = missingEntitySlots(definition.requiredEntitySlots(), response.entities());
        if (!missingEntitySlots.isEmpty()) {
            return BackendActionDraft.skipped(
                    "missing_required_entities",
                    "缺少执行所需槽位：" + String.join("、", missingEntitySlots)
            );
        }

        Map<String, Object> pathVariables = renderMap(definition.pathVariables(), response, candidate);
        Map<String, Object> queryParameters = renderMap(definition.queryParameters(), response, candidate);
        Map<String, Object> body = renderMap(definition.body(), response, candidate);
        List<String> remainingClientFields = definition.requiredClientFields().stream()
                .filter(field -> clientContext == null || !clientContext.provides(field))
                .toList();
        return new BackendActionDraft(
                definition.status(),
                bridgeVersion,
                actionType,
                definition.nextAction(),
                true,
                definition.executableByBackend(),
                definition.authorizationRequired(),
                definition.method(),
                definition.pathTemplate(),
                renderPath(definition.pathTemplate(), pathVariables),
                definition.contentType(),
                pathVariables,
                queryParameters,
                body,
                remainingClientFields,
                candidate,
                definition.message()
        );
    }

    private BackendActionDraft buildCollectionDraft(
            IntentRecognitionResponse response,
            ActionBridgeDefinition definition
    ) {
        ActionPlan plan = response.actionPlan();
        if (plan == null || !"collection".equals(plan.planKind())) {
            return BackendActionDraft.skipped("collection_plan_missing", "缺少集合 ActionPlan，不能生成批量请求草稿。");
        }
        if (!"collection_review_required".equals(plan.status())) {
            return BackendActionDraft.skipped("collection_preview_not_ready", "集合预览尚未就绪，不能生成批量请求草稿。");
        }

        List<String> missingBindings = missingBindings(definition.requiredBindings(), plan.bindings());
        if (!missingBindings.isEmpty()) {
            return BackendActionDraft.skipped(
                    "missing_required_bindings",
                    "缺少执行所需绑定：" + String.join("、", missingBindings)
            );
        }

        ActionPlanBinding sourceCollection = sourceCollectionBinding(plan);
        if (sourceCollection == null || !"resolved".equals(sourceCollection.status())) {
            return BackendActionDraft.skipped("collection_preview_not_ready", "源集合预览未完成，不能生成批量请求草稿。");
        }
        if (sourceCollection.candidates().isEmpty()) {
            return BackendActionDraft.skipped("collection_preview_empty", "源集合为空，不能生成批量请求草稿。");
        }
        if (sourceCollection.count() == null || sourceCollection.count() != sourceCollection.candidates().size()) {
            return BackendActionDraft.skipped(
                    "collection_preview_not_executable",
                    "集合预览未包含全部候选，暂不生成批量请求草稿。"
            );
        }
        if (nodeIds(sourceCollection).size() != sourceCollection.candidates().size()) {
            return BackendActionDraft.skipped("missing_candidate_fields", "集合候选缺少 nodeId，不能生成批量请求草稿。");
        }

        ActionPlanBinding targetParent = plan.bindings().get("targetParent");
        if (definition.requiredBindings().contains("targetParent")
                && (targetParent == null
                || targetParent.selectedCandidate() == null
                || targetParent.selectedCandidate().nodeId() == null)) {
            return BackendActionDraft.skipped("missing_target_candidate", "尚未锁定目标目录，不能生成批量请求草稿。");
        }

        Map<String, Object> pathVariables = renderMap(definition.pathVariables(), response, null, plan);
        Map<String, Object> queryParameters = renderMap(definition.queryParameters(), response, null, plan);
        Map<String, Object> body = renderMap(definition.body(), response, null, plan);
        return new BackendActionDraft(
                definition.status(),
                bridgeVersion,
                response.actionDraft().type(),
                definition.nextAction(),
                true,
                definition.executableByBackend(),
                definition.authorizationRequired(),
                definition.method(),
                definition.pathTemplate(),
                renderPath(definition.pathTemplate(), pathVariables),
                definition.contentType(),
                pathVariables,
                queryParameters,
                body,
                definition.requiredClientFields(),
                targetParent == null ? null : targetParent.selectedCandidate(),
                definition.message()
        );
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

    private List<String> missingCandidateFields(List<String> requiredFields, CandidateItem candidate) {
        return requiredFields.stream()
                .filter(field -> !hasCandidateField(candidate, field))
                .toList();
    }

    private boolean hasCandidateField(CandidateItem candidate, String field) {
        Object value = switch (field) {
            case "nodeId" -> candidate.nodeId();
            case "parentId" -> candidate.parentId();
            case "name" -> candidate.name();
            case "type" -> candidate.type();
            case "size" -> candidate.size();
            case "extension" -> candidate.extension();
            case "mimeType" -> candidate.mimeType();
            case "updatedAt" -> candidate.updatedAt();
            default -> null;
        };
        return value != null && !String.valueOf(value).isBlank();
    }

    private List<String> missingEntitySlots(List<String> requiredSlots, Map<String, Object> entities) {
        return requiredSlots.stream()
                .filter(slot -> entities == null
                        || !entities.containsKey(slot)
                        || String.valueOf(entities.get(slot)).isBlank())
                .toList();
    }

    private Map<String, Object> renderMap(
            Map<String, Object> template,
            IntentRecognitionResponse response,
            CandidateItem candidate
    ) {
        return renderMap(template, response, candidate, null);
    }

    private Map<String, Object> renderMap(
            Map<String, Object> template,
            IntentRecognitionResponse response,
            CandidateItem candidate,
            ActionPlan plan
    ) {
        Map<String, Object> rendered = new LinkedHashMap<>();
        template.forEach((key, value) -> rendered.put(key, renderValue(value, response, candidate, plan)));
        return rendered;
    }

    private Object renderValue(Object value, IntentRecognitionResponse response, CandidateItem candidate) {
        return renderValue(value, response, candidate, null);
    }

    private Object renderValue(Object value, IntentRecognitionResponse response, CandidateItem candidate, ActionPlan plan) {
        if (value instanceof String stringValue) {
            return resolveExpression(stringValue, response, candidate, plan);
        }
        if (value instanceof List<?> listValue) {
            return listValue.stream()
                    .map(item -> renderValue(item, response, candidate, plan))
                    .toList();
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> rendered = new LinkedHashMap<>();
            mapValue.forEach((key, item) -> rendered.put(String.valueOf(key), renderValue(item, response, candidate, plan)));
            return rendered;
        }
        return value;
    }

    private Object resolveExpression(String expression, IntentRecognitionResponse response, CandidateItem candidate) {
        return resolveExpression(expression, response, candidate, null);
    }

    private Object resolveExpression(
            String expression,
            IntentRecognitionResponse response,
            CandidateItem candidate,
            ActionPlan plan
    ) {
        if (!expression.startsWith("$")) {
            return expression;
        }
        Object bindingValue = resolveBindingExpression(expression, plan);
        if (bindingValue != null) {
            return bindingValue;
        }
        if (candidate == null) {
            return resolveEntityExpression(expression, response.entities());
        }
        return switch (expression) {
            case "$candidate.nodeId" -> candidate.nodeId();
            case "$candidate.parentId" -> candidate.parentId();
            case "$candidate.name" -> candidate.name();
            case "$candidate.type" -> candidate.type();
            case "$candidate.size" -> candidate.size();
            case "$candidate.extension" -> candidate.extension();
            case "$candidate.mimeType" -> candidate.mimeType();
            case "$candidate.updatedAt" -> candidate.updatedAt();
            default -> resolveEntityExpression(expression, response.entities());
        };
    }

    private Object resolveBindingExpression(String expression, ActionPlan plan) {
        if (plan == null || !expression.startsWith("$bindings.")) {
            return null;
        }
        if ("$bindings.sourceCollection.nodeIds".equals(expression)) {
            ActionPlanBinding sourceCollection = sourceCollectionBinding(plan);
            return sourceCollection == null ? List.of() : nodeIds(sourceCollection);
        }
        if ("$bindings.targetParent.nodeId".equals(expression)) {
            ActionPlanBinding targetParent = plan.bindings().get("targetParent");
            CandidateItem candidate = targetParent == null ? null : targetParent.selectedCandidate();
            return candidate == null ? "" : candidate.nodeId();
        }
        if ("$bindings.targetParent.path".equals(expression)) {
            ActionPlanBinding targetParent = plan.bindings().get("targetParent");
            CandidateItem candidate = targetParent == null ? null : targetParent.selectedCandidate();
            return candidate == null ? "" : candidate.path();
        }
        return null;
    }

    private Object resolveEntityExpression(String expression, Map<String, Object> entities) {
        String prefix = "$entities.";
        if (!expression.startsWith(prefix)) {
            return "";
        }
        String key = expression.substring(prefix.length());
        Object value = entities == null ? null : entities.get(key);
        return value == null ? "" : value;
    }

    private String renderPath(String pathTemplate, Map<String, Object> pathVariables) {
        String path = pathTemplate == null ? "" : pathTemplate;
        for (Map.Entry<String, Object> entry : pathVariables.entrySet()) {
            path = path.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return path;
    }

    private Map<String, ActionBridgeDefinition> loadActions(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        Map<String, ActionBridgeDefinition> actions = new LinkedHashMap<>();
        node.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            actions.put(entry.getKey(), new ActionBridgeDefinition(
                    value.path("enabled").asBoolean(true),
                    value.path("status").asText("backend_action_ready"),
                    value.path("next_action").asText("handoff_to_backend"),
                    value.path("executable_by_backend").asBoolean(false),
                    value.path("authorization_required").asBoolean(true),
                    value.path("method").asText(""),
                    value.path("path_template").asText(""),
                    value.path("content_type").asText(""),
                    stringList(value.path("required_candidate_fields")),
                    stringList(value.path("required_entity_slots")),
                    stringList(value.path("required_client_fields")),
                    stringList(value.path("required_bindings")),
                    objectMap(value.path("path_variables")),
                    objectMap(value.path("query_parameters")),
                    objectMap(value.path("body")),
                    value.path("message").asText("")
            ));
        });
        return Map.copyOf(actions);
    }

    private Map<String, Object> objectMap(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        node.properties().forEach(entry -> result.put(entry.getKey(), objectValue(entry.getValue())));
        return Collections.unmodifiableMap(result);
    }

    private Object objectValue(JsonNode node) {
        if (node.isObject()) {
            return objectMap(node);
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(item -> values.add(objectValue(item)));
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
        return node.asText("");
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

    private ActionPlanBinding sourceCollectionBinding(ActionPlan plan) {
        for (ActionPlanBinding binding : plan.bindings().values()) {
            if ("source_collection".equals(binding.kind())) {
                return binding;
            }
        }
        return null;
    }

    private List<Long> nodeIds(ActionPlanBinding binding) {
        return binding.candidates().stream()
                .map(CandidateItem::nodeId)
                .filter(nodeId -> nodeId != null)
                .toList();
    }

    private List<String> missingBindings(
            List<String> requiredBindings,
            Map<String, ActionPlanBinding> bindings
    ) {
        return requiredBindings.stream()
                .filter(binding -> bindings == null || !bindings.containsKey(binding))
                .toList();
    }

    private record ActionBridgeDefinition(
            boolean enabled,
            String status,
            String nextAction,
            boolean executableByBackend,
            boolean authorizationRequired,
            String method,
            String pathTemplate,
            String contentType,
            List<String> requiredCandidateFields,
            List<String> requiredEntitySlots,
            List<String> requiredClientFields,
            List<String> requiredBindings,
            Map<String, Object> pathVariables,
            Map<String, Object> queryParameters,
            Map<String, Object> body,
            String message
    ) {
    }
}
