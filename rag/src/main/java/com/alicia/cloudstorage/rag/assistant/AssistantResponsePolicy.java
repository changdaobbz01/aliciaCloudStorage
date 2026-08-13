package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class AssistantResponsePolicy {

    private static final String CONFIG_PATH = "rag/conversation/assistant_response_policy.json";

    private final List<String> promptFacts;
    private final List<PolicyRule> rules;

    AssistantResponsePolicy(RagConfigLoader configLoader) {
        JsonNode config = configLoader.loadJson(CONFIG_PATH);
        this.promptFacts = stringList(config.path("promptFacts"));
        List<PolicyRule> loadedRules = new ArrayList<>();
        for (JsonNode node : config.path("rules")) {
            if (!node.path("enabled").asBoolean(true)) {
                continue;
            }
            loadedRules.add(new PolicyRule(
                    node.path("id").asText("unnamed_policy"),
                    Set.copyOf(stringList(node.path("intentIds"))),
                    Set.copyOf(stringList(node.path("actionTypes"))),
                    Set.copyOf(stringList(node.path("nextActions"))),
                    pattern(node.path("userPattern").asText("")),
                    pattern(node.path("requiredAssistantPattern").asText("")),
                    pattern(node.path("forbiddenAssistantPattern").asText("")),
                    node.path("fallbackMessage").asText("").trim()
            ));
        }
        this.rules = List.copyOf(loadedRules);
    }

    List<String> promptFacts() {
        return promptFacts;
    }

    PolicyDecision evaluate(
            String userMessage,
            String assistantText,
            String intentId,
            String actionType,
            String nextAction
    ) {
        String safeUserMessage = userMessage == null ? "" : userMessage;
        String safeAssistantText = assistantText == null ? "" : assistantText;
        List<String> violations = new ArrayList<>();
        String fallbackMessage = "";

        for (PolicyRule rule : rules) {
            if (!rule.applies(safeUserMessage, intentId, actionType, nextAction)) {
                continue;
            }
            boolean missingRequiredFact = rule.requiredAssistantPattern() != null
                    && !rule.requiredAssistantPattern().matcher(safeAssistantText).find();
            boolean containsForbiddenFact = rule.forbiddenAssistantPattern() != null
                    && rule.forbiddenAssistantPattern().matcher(safeAssistantText).find();
            if (!missingRequiredFact && !containsForbiddenFact) {
                continue;
            }
            violations.add(rule.id());
            if (fallbackMessage.isBlank() && !rule.fallbackMessage().isBlank()) {
                fallbackMessage = rule.fallbackMessage();
            }
        }
        return new PolicyDecision(violations.isEmpty(), fallbackMessage, List.copyOf(violations));
    }

    private List<String> stringList(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        node.forEach(item -> {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        });
        return List.copyOf(values);
    }

    private Pattern pattern(String value) {
        return value == null || value.isBlank()
                ? null
                : Pattern.compile(value, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private record PolicyRule(
            String id,
            Set<String> intentIds,
            Set<String> actionTypes,
            Set<String> nextActions,
            Pattern userPattern,
            Pattern requiredAssistantPattern,
            Pattern forbiddenAssistantPattern,
            String fallbackMessage
    ) {
        boolean applies(String userMessage, String intentId, String actionType, String nextAction) {
            return (intentIds.isEmpty() || intentIds.contains(intentId))
                    && (actionTypes.isEmpty() || actionTypes.contains(actionType))
                    && (nextActions.isEmpty() || nextActions.contains(nextAction))
                    && (userPattern == null || userPattern.matcher(userMessage).find());
        }
    }

    record PolicyDecision(boolean allowed, String fallbackMessage, List<String> violations) {
    }
}
