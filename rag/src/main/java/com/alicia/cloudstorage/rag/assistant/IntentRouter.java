package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class IntentRouter {

    private final Map<String, IntentDefinition> intentsById;
    private final List<IntentRule> intentRules;
    private final EntityExtractor entityExtractor;
    private final FallbackPolicy fallbackPolicy;
    private final List<String> allowedNextActions;
    private final List<String> allowedRisks;
    private final List<String> allowedActionTypes;
    private final Set<String> forbiddenActionParameterKeys;
    private final Map<String, String> slotAliases;
    private final Map<String, SlotDefinition> slotsById;

    public IntentRouter(RagConfigLoader configLoader, EntityExtractor entityExtractor) {
        JsonNode config = configLoader.loadJson("rag/conversation/intents.json");
        this.slotAliases = loadSlotAliases(config.path("schema").path("slot_aliases"));
        this.slotsById = loadSlots(config.path("slots"));
        this.allowedNextActions = schemaStringList(config, "allowed_next_actions", List.of(
                "ask_clarification",
                "wait_for_backend_binding",
                "show_search_results",
                "wait_for_candidate_selection",
                "wait_for_user_confirmation",
                "handoff_to_backend",
                "handoff_to_client_upload"
        ));
        this.allowedRisks = schemaStringList(config, "allowed_risks", List.of("none", "low", "medium", "high"));
        this.allowedActionTypes = schemaStringList(config, "allowed_action_types", List.of(
                "none",
                "search",
                "rename",
                "share",
                "delete",
                "upload_target"
        ));
        this.forbiddenActionParameterKeys = normalizedSet(schemaStringList(config, "forbidden_action_parameter_keys", List.of()));
        this.intentsById = loadIntents(config);
        this.intentRules = loadIntentRules(configLoader);
        this.entityExtractor = entityExtractor;
        this.fallbackPolicy = new FallbackPolicy(
                config.path("fallback_policy").path("low_confidence_threshold").asDouble(0.65),
                config.path("fallback_policy").path("default_intent_id").asText("file_search"),
                config.path("fallback_policy").path("when_empty_message").asText("fallback"),
                config.path("fallback_policy").path("when_out_of_scope").asText("fallback")
        );
        validateRules();
    }

    public IntentRouteResult route(String message) {
        String cleanMessage = message == null ? "" : message.trim();
        Map<String, String> entities = entityExtractor.extract(cleanMessage);
        if (cleanMessage.isBlank()) {
            return buildResult(fallbackPolicy.emptyMessageIntentId(), entities, 0.2, "用户输入为空");
        }

        for (IntentRule rule : intentRules) {
            if (rule.matches(cleanMessage)) {
                return buildResult(rule.intentId(), entities, rule.confidence(), rule.reason());
            }
        }

        return buildResult(
                fallbackPolicy.outOfScopeIntentId(),
                entities,
                fallbackPolicy.lowConfidenceThreshold(),
                "未命中配置化规则，按兜底澄清处理"
        );
    }

    public IntentDefinition getIntent(String intentId) {
        IntentDefinition intent = intentsById.get(intentId);
        return intent != null ? intent : intentsById.get("fallback");
    }

    public boolean hasIntent(String intentId) {
        return intentsById.containsKey(intentId);
    }

    public boolean isAllowedNextAction(String nextAction) {
        return allowedNextActions.contains(nextAction);
    }

    public boolean isAllowedRisk(String risk) {
        return allowedRisks.contains(risk);
    }

    public boolean isAllowedActionType(String actionType) {
        return allowedActionTypes.contains(actionType);
    }

    public boolean isForbiddenActionParameterKey(String key) {
        return forbiddenActionParameterKeys.contains(normalizeKey(key));
    }

    public String normalizeSlotId(String slotId) {
        String trimmed = slotId == null ? "" : slotId.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        return slotAliases.getOrDefault(trimmed, slotAliases.getOrDefault(normalizeKey(trimmed), trimmed));
    }

    public String slotLabel(String slotId) {
        SlotDefinition slot = slotsById.get(normalizeSlotId(slotId));
        return slot == null ? slotId : slot.label();
    }

    public String slotClarification(String slotId) {
        SlotDefinition slot = slotsById.get(normalizeSlotId(slotId));
        return slot == null ? "" : slot.clarification();
    }

    private IntentRouteResult buildResult(
            String intentId,
            Map<String, String> entities,
            double confidence,
            String reason
    ) {
        IntentDefinition intent = getIntent(intentId);
        List<String> missingSlots = intent.requiredSlots().stream()
                .filter(slot -> !entities.containsKey(slot))
                .toList();
        return new IntentRouteResult(
                intent.id(),
                intent.name(),
                intent.taskType(),
                confidence,
                reason,
                entities,
                missingSlots,
                intent.nextAction()
        );
    }

    private Map<String, IntentDefinition> loadIntents(JsonNode config) {
        Map<String, IntentDefinition> intents = new LinkedHashMap<>();
        for (JsonNode node : config.path("intents")) {
            IntentDefinition intent = new IntentDefinition(
                    node.path("id").asText(),
                    node.path("name").asText(),
                    node.path("task_type").asText(),
                    normalizeSlotList(node.path("required_slots")),
                    normalizeSlotList(node.path("optional_slots")),
                    allowedSlots(node),
                    node.path("risk").asText("none"),
                    node.path("requires_confirmation").asBoolean(false),
                    node.path("action_type").asText("none"),
                    node.path("candidate_type").asText("ANY"),
                    normalizeSlotId(node.path("target_missing_slot").asText("")),
                    node.path("next_action").asText("ask_clarification"),
                    node.path("clarification_question").asText("")
            );
            intents.put(intent.id(), intent);
        }
        return intents;
    }

    private Map<String, String> loadSlotAliases(JsonNode node) {
        Map<String, String> aliases = new HashMap<>();
        if (!node.isObject()) {
            return Map.of();
        }
        node.properties().forEach(entry -> {
            String alias = entry.getKey();
            String target = entry.getValue().asText("");
            if (!alias.isBlank() && !target.isBlank()) {
                aliases.put(alias, target);
                aliases.put(normalizeKey(alias), target);
            }
        });
        return Map.copyOf(aliases);
    }

    private Map<String, SlotDefinition> loadSlots(JsonNode node) {
        Map<String, SlotDefinition> slots = new LinkedHashMap<>();
        if (!node.isObject()) {
            return Map.of();
        }
        node.properties().forEach(entry -> {
            String id = normalizeSlotId(entry.getKey());
            JsonNode value = entry.getValue();
            if (!id.isBlank()) {
                slots.put(id, new SlotDefinition(
                        id,
                        value.path("label").asText(id),
                        value.path("clarification").asText("")
                ));
            }
        });
        return Map.copyOf(slots);
    }

    private List<String> allowedSlots(JsonNode node) {
        List<String> configured = normalizeSlotList(node.path("allowed_slots"));
        if (!configured.isEmpty()) {
            return configured;
        }

        LinkedHashSet<String> slots = new LinkedHashSet<>();
        slots.addAll(normalizeSlotList(node.path("required_slots")));
        slots.addAll(normalizeSlotList(node.path("optional_slots")));
        return List.copyOf(slots);
    }

    private List<IntentRule> loadIntentRules(RagConfigLoader configLoader) {
        List<Map<String, String>> rows = configLoader.loadCsv("rag/conversation/intent_rules.csv");
        List<IntentRule> rules = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Map<String, String> row = rows.get(index);
            if (row.getOrDefault("intent_id", "").isBlank()) {
                continue;
            }
            rules.add(new IntentRule(
                    row.get("intent_id"),
                    parseBool(row.getOrDefault("enabled", "true")),
                    parseInt(row.get("priority")),
                    parseDouble(row.get("confidence")),
                    TextSupport.parseKeywordGroups(row.get("required_groups")),
                    TextSupport.parseKeywords(row.get("excluded_keywords")),
                    row.getOrDefault("reason", ""),
                    index + 2
            ));
        }
        rules.sort(Comparator.comparingInt(IntentRule::priority).reversed().thenComparingInt(IntentRule::rowNumber));
        return List.copyOf(rules);
    }

    private void validateRules() {
        List<String> missing = intentRules.stream()
                .map(IntentRule::intentId)
                .filter(intentId -> !intentsById.containsKey(intentId))
                .distinct()
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("intent_rules.csv 引用了未定义意图: " + String.join(", ", missing));
        }
    }

    private static List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return List.copyOf(values);
    }

    private List<String> normalizeSlotList(JsonNode node) {
        return stringList(node).stream()
                .map(this::normalizeSlotId)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private List<String> schemaStringList(JsonNode config, String key, List<String> fallback) {
        List<String> values = stringList(config.path("schema").path(key));
        return values.isEmpty() ? fallback : values;
    }

    private Set<String> normalizedSet(List<String> values) {
        return values.stream()
                .map(IntentRouter::normalizeKey)
                .filter(item -> !item.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String normalizeKey(String key) {
        return key == null
                ? ""
                : key.toLowerCase(Locale.ROOT).replaceAll("[_\\-\\s]", "");
    }

    private static boolean parseBool(String value) {
        return value != null && List.of("1", "true", "yes", "y").contains(value.trim().toLowerCase());
    }

    private static int parseInt(String value) {
        return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
    }

    private static double parseDouble(String value) {
        return value == null || value.isBlank() ? 0.0 : Double.parseDouble(value.trim());
    }

    private record FallbackPolicy(
            double lowConfidenceThreshold,
            String defaultIntentId,
            String emptyMessageIntentId,
            String outOfScopeIntentId
    ) {
    }

    public record IntentDefinition(
            String id,
            String name,
            String taskType,
            List<String> requiredSlots,
            List<String> optionalSlots,
            List<String> allowedSlots,
            String risk,
            boolean requiresConfirmation,
            String actionType,
            String candidateType,
            String targetMissingSlot,
            String nextAction,
            String clarificationQuestion
    ) {
    }

    public record SlotDefinition(
            String id,
            String label,
            String clarification
    ) {
    }

    private record IntentRule(
            String intentId,
            boolean enabled,
            int priority,
            double confidence,
            List<List<String>> requiredGroups,
            List<String> excludedKeywords,
            String reason,
            int rowNumber
    ) {
        boolean matches(String message) {
            if (!enabled) {
                return false;
            }
            if (!excludedKeywords.isEmpty() && TextSupport.containsAny(message, excludedKeywords)) {
                return false;
            }
            return requiredGroups.stream().allMatch(group -> TextSupport.containsAny(message, group));
        }
    }

    public record IntentRouteResult(
            String intent,
            String intentName,
            String taskType,
            double confidence,
            String reason,
            Map<String, String> entities,
            List<String> missingSlots,
            String nextAction
    ) {
    }
}
