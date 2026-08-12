package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class IntentSemanticTemplateCoverageTest {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final List<String> FORBIDDEN_ACTION_PARAMETER_KEYS = List.of(
            "nodeId",
            "node_id",
            "fileId",
            "file_id",
            "folderId",
            "folder_id",
            "ownerId",
            "owner_id",
            "userId",
            "user_id",
            "storagePath",
            "storage_path",
            "objectKey",
            "object_key",
            "cosKey",
            "cos_key"
    );

    private RagConfigLoader configLoader;
    private IntentRecognitionService service;

    @BeforeEach
    void setUp() {
        configLoader = new RagConfigLoader(new ObjectMapper());
        IntentRouter intentRouter = new IntentRouter(configLoader, new EntityExtractor(configLoader));
        service = new IntentRecognitionService(message -> Optional.empty(), intentRouter, configLoader);
    }

    @Test
    void localFallbackCoversConfiguredSemanticTemplateCorpus() {
        Map<String, Object> config = configLoader.loadJsonMap("rag/conversation/semantic_intent_templates.json");
        List<SemanticCase> cases = expandCases(config);
        int minimumExpandedCases = number(config.get("minimumExpandedCases")).intValue();

        assertThat(cases).hasSizeGreaterThanOrEqualTo(minimumExpandedCases);

        for (SemanticCase semanticCase : cases) {
            IntentRecognitionResponse response = service.recognize(semanticCase.message());

            assertThat(response.intentId())
                    .as("%s intent for [%s]", semanticCase.id(), semanticCase.message())
                    .isEqualTo(semanticCase.intentId());
            assertThat(response.actionDraft().type())
                    .as("%s action type for [%s]", semanticCase.id(), semanticCase.message())
                    .isEqualTo(semanticCase.actionDraftType());
            assertThat(response.safety().risk())
                    .as("%s risk for [%s]", semanticCase.id(), semanticCase.message())
                    .isEqualTo(semanticCase.risk());
            assertThat(response.missingSlots())
                    .as("%s missing slots for [%s]", semanticCase.id(), semanticCase.message())
                    .isEmpty();

            for (String entityKey : semanticCase.requiredEntities()) {
                String expectedValue = expectedEntityValue(entityKey, semanticCase.bindings().get(entityKey));
                assertThat(response.entities())
                        .as("%s entity %s for [%s]", semanticCase.id(), entityKey, semanticCase.message())
                        .containsEntry(entityKey, expectedValue);
                assertThat(response.actionDraft().parameters())
                        .as("%s action parameter %s for [%s]", semanticCase.id(), entityKey, semanticCase.message())
                        .containsEntry(entityKey, expectedValue);
            }

            assertThat(response.actionDraft().parameters().keySet())
                    .as("%s forbidden parameters for [%s]", semanticCase.id(), semanticCase.message())
                    .doesNotContainAnyElementsOf(FORBIDDEN_ACTION_PARAMETER_KEYS);
        }
    }

    private List<SemanticCase> expandCases(Map<String, Object> config) {
        Map<String, Object> entityPools = map(config, "entityPools");
        List<SemanticCase> cases = new ArrayList<>();

        for (Object item : list(config, "suites")) {
            Map<String, Object> suite = map(item);
            String suiteId = stringValue(suite, "id");
            String intentId = stringValue(suite, "intentId");
            String actionDraftType = stringValue(suite, "actionDraftType");
            String risk = stringValue(suite, "risk");
            List<String> requiredEntities = stringList(suite, "requiredEntities");
            Map<String, Object> suitePools = mapOrEmpty(suite.get("pools"));

            for (String template : stringList(suite, "templates")) {
                List<Map<String, String>> bindings = expandBindings(placeholders(template), suitePools, entityPools);
                for (Map<String, String> binding : bindings) {
                    cases.add(new SemanticCase(
                            suiteId,
                            render(template, binding),
                            intentId,
                            actionDraftType,
                            risk,
                            requiredEntities,
                            binding
                    ));
                }
            }
        }

        return List.copyOf(cases);
    }

    private List<Map<String, String>> expandBindings(
            List<String> placeholders,
            Map<String, Object> suitePools,
            Map<String, Object> entityPools
    ) {
        if (placeholders.isEmpty()) {
            return List.of(Map.of());
        }

        List<Map<String, String>> bindings = new ArrayList<>();
        collectBindings(placeholders, suitePools, entityPools, 0, new LinkedHashMap<>(), bindings);
        return bindings;
    }

    private void collectBindings(
            List<String> placeholders,
            Map<String, Object> suitePools,
            Map<String, Object> entityPools,
            int index,
            Map<String, String> current,
            List<Map<String, String>> bindings
    ) {
        if (index >= placeholders.size()) {
            bindings.add(Map.copyOf(current));
            return;
        }

        String placeholder = placeholders.get(index);
        String poolName = String.valueOf(suitePools.getOrDefault(placeholder, placeholder));
        for (String value : stringList(entityPools, poolName)) {
            current.put(placeholder, value);
            collectBindings(placeholders, suitePools, entityPools, index + 1, current, bindings);
            current.remove(placeholder);
        }
    }

    private List<String> placeholders(String template) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        Set<String> placeholders = new LinkedHashSet<>();
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return List.copyOf(placeholders);
    }

    private String render(String template, Map<String, String> bindings) {
        String result = template;
        for (Map.Entry<String, String> entry : bindings.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String expectedEntityValue(String entityKey, String configuredValue) {
        if ("extension".equals(entityKey)) {
            return configuredValue.toUpperCase();
        }
        return configuredValue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object source) {
        return (Map<String, Object>) source;
    }

    private Map<String, Object> map(Map<String, Object> source, String key) {
        return map(source.get(key));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOrEmpty(Object source) {
        return source instanceof Map<?, ?> ? (Map<String, Object>) source : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Object> list(Map<String, Object> source, String key) {
        return (List<Object>) source.get(key);
    }

    private List<String> stringList(Map<String, Object> source, String key) {
        return list(source, key).stream()
                .map(String::valueOf)
                .toList();
    }

    private String stringValue(Map<String, Object> source, String key) {
        return String.valueOf(source.get(key));
    }

    private Number number(Object source) {
        return (Number) source;
    }

    private record SemanticCase(
            String id,
            String message,
            String intentId,
            String actionDraftType,
            String risk,
            List<String> requiredEntities,
            Map<String, String> bindings
    ) {
    }
}
