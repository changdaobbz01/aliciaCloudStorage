package com.alicia.cloudstorage.rag.assistant;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EntityExtractor {

    private final List<EntityRule> rules;

    public EntityExtractor(RagConfigLoader configLoader) {
        this.rules = loadEntityRules(configLoader);
    }

    public Map<String, String> extract(String message) {
        Map<String, String> entities = new LinkedHashMap<>();
        for (EntityRule rule : rules) {
            if (!rule.enabled() || entities.containsKey(rule.entityId())) {
                continue;
            }
            String value = extractWithRule(message, rule);
            if (!value.isBlank()) {
                entities.put(rule.entityId(), rule.normalizeUpper() ? value.toUpperCase() : value);
            }
        }
        return entities;
    }

    private String extractWithRule(String message, EntityRule rule) {
        if ("regex_capture".equals(rule.strategy())) {
            Matcher matcher = Pattern.compile(rule.patterns(), Pattern.CASE_INSENSITIVE).matcher(message);
            return matcher.find() && matcher.groupCount() >= 1 ? TextSupport.sanitizeNodeName(matcher.group(1)) : "";
        }

        if ("regex".equals(rule.strategy())) {
            Matcher matcher = Pattern.compile(rule.patterns(), Pattern.CASE_INSENSITIVE).matcher(message);
            return matcher.find() ? TextSupport.sanitizeNodeName(matcher.group()) : "";
        }

        if ("keyword".equals(rule.strategy())) {
            String normalized = TextSupport.normalizeText(message);
            return TextSupport.parseKeywords(rule.patterns()).stream()
                    .filter(keyword -> normalized.contains(TextSupport.normalizeText(keyword)))
                    .findFirst()
                    .orElse("");
        }

        throw new IllegalStateException("entity_rules.csv 第 " + rule.rowNumber() + " 行使用了不支持的策略: " + rule.strategy());
    }

    private List<EntityRule> loadEntityRules(RagConfigLoader configLoader) {
        List<Map<String, String>> rows = configLoader.loadCsv("rag/conversation/entity_rules.csv");
        List<EntityRule> result = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Map<String, String> row = rows.get(index);
            if (row.getOrDefault("entity_id", "").isBlank()) {
                continue;
            }
            result.add(new EntityRule(
                    row.get("entity_id"),
                    parseBool(row.getOrDefault("enabled", "true")),
                    row.getOrDefault("strategy", ""),
                    row.getOrDefault("patterns", ""),
                    parseBool(row.getOrDefault("normalize_upper", "false")),
                    index + 2
            ));
        }
        return List.copyOf(result);
    }

    private boolean parseBool(String value) {
        return value != null && List.of("1", "true", "yes", "y").contains(value.trim().toLowerCase());
    }

    private record EntityRule(
            String entityId,
            boolean enabled,
            String strategy,
            String patterns,
            boolean normalizeUpper,
            int rowNumber
    ) {
    }
}
