package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class SemanticCapabilityBoundaryGuard {

    private static final String CONFIG_PATH = "rag/conversation/capability_boundaries.json";

    private final List<BoundaryRule> rules;

    public SemanticCapabilityBoundaryGuard(RagConfigLoader configLoader) {
        this.rules = loadRules(configLoader.loadJson(CONFIG_PATH));
    }

    public Optional<BoundaryDecision> evaluate(String message) {
        String value = message == null ? "" : message.trim();
        if (value.isBlank()) {
            return Optional.empty();
        }
        return rules.stream()
                .filter(rule -> rule.matches(value))
                .map(rule -> new BoundaryDecision(rule.id(), rule.reason(), rule.guidance()))
                .findFirst();
    }

    public Optional<BoundaryDecision> evaluateSemanticContract(
            String intentId,
            String operation,
            Map<String, ?> entities
    ) {
        Map<String, ?> safeEntities = entities == null ? Map.of() : entities;
        if (!"DELETE".equalsIgnoreCase(operation)
                || !isPresent(safeEntities.get("extension"))
                || isExactExtensionTarget(
                        safeEntities.get("target_name"),
                        safeEntities.get("extension")
                )) {
            return Optional.empty();
        }
        return decisionFor("unsupported_delete_extension_selector");
    }

    private Optional<BoundaryDecision> decisionFor(String id) {
        return rules.stream()
                .filter(rule -> rule.id().equals(id))
                .map(rule -> new BoundaryDecision(rule.id(), rule.reason(), rule.guidance()))
                .findFirst();
    }

    private boolean isPresent(Object value) {
        return value != null && !String.valueOf(value).trim().isBlank();
    }

    private boolean isExactExtensionTarget(Object targetName, Object extension) {
        if (!isPresent(targetName) || !isPresent(extension)) {
            return false;
        }
        String target = String.valueOf(targetName).trim().toLowerCase(Locale.ROOT);
        String suffix = String.valueOf(extension).trim().toLowerCase(Locale.ROOT);
        suffix = suffix.startsWith(".") ? suffix.substring(1) : suffix;
        return !suffix.isBlank() && target.endsWith("." + suffix);
    }

    private List<BoundaryRule> loadRules(JsonNode root) {
        List<BoundaryRule> loaded = new ArrayList<>();
        root.path("boundaries").forEach(node -> {
            if (!node.path("enabled").asBoolean(true)) {
                return;
            }
            String pattern = node.path("pattern").asText("").trim();
            if (pattern.isBlank()) {
                return;
            }
            String excluded = node.path("excluded_pattern").asText("").trim();
            loaded.add(new BoundaryRule(
                    node.path("id").asText(""),
                    Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                    excluded.isBlank()
                            ? null
                            : Pattern.compile(excluded, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                    node.path("reason").asText("当前请求包含尚未支持的组合规则。"),
                    node.path("guidance").asText("请把需求拆成更明确的步骤后再试。")
            ));
        });
        return List.copyOf(loaded);
    }

    public record BoundaryDecision(String id, String reason, String guidance) {
        public String userMessage() {
            return reason + guidance;
        }
    }

    private record BoundaryRule(
            String id,
            Pattern pattern,
            Pattern excludedPattern,
            String reason,
            String guidance
    ) {
        boolean matches(String message) {
            return pattern.matcher(message).find()
                    && (excludedPattern == null || !excludedPattern.matcher(message).find());
        }
    }
}
