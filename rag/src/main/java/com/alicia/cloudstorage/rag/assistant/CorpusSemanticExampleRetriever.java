package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class CorpusSemanticExampleRetriever implements SemanticExampleRetriever {

    private static final String CORPUS_PATH = "rag/corpus/cloud_drive_semantic_examples.json";
    private static final String BOUNDARY_PATH = "rag/corpus/cloud_drive_semantic_boundaries.json";
    private static final double MIN_SCORE = 0.12;

    private final List<CorpusExample> examples;
    private final List<CorpusBoundary> boundaries;

    public CorpusSemanticExampleRetriever(RagConfigLoader configLoader) {
        this.examples = loadExamples(configLoader.loadJson(CORPUS_PATH));
        this.boundaries = loadBoundaries(configLoader.loadJson(BOUNDARY_PATH));
    }

    @Override
    public List<SemanticExample> retrieve(String message, int limit) {
        String normalized = normalize(message);
        int safeLimit = Math.max(0, Math.min(limit, 8));
        if (normalized.isBlank() || safeLimit == 0) {
            return List.of();
        }
        Set<String> queryGrams = grams(normalized);
        return examples.stream()
                .map(example -> example.toResult(similarity(normalized, queryGrams, example.normalized(), example.grams())))
                .filter(example -> example.score() >= MIN_SCORE)
                .sorted(Comparator.comparingDouble(SemanticExample::score).reversed()
                        .thenComparing(SemanticExample::id))
                .limit(safeLimit)
                .toList();
    }

    @Override
    public List<SemanticBoundary> retrieveBoundaries(String message, int limit) {
        String normalized = normalize(message);
        int safeLimit = Math.max(0, Math.min(limit, 8));
        if (normalized.isBlank() || safeLimit == 0) {
            return List.of();
        }
        Set<String> queryGrams = grams(normalized);
        return boundaries.stream()
                .map(boundary -> boundary.toResult(similarity(
                        normalized,
                        queryGrams,
                        boundary.normalized(),
                        boundary.grams()
                )))
                .filter(boundary -> boundary.score() >= MIN_SCORE)
                .sorted(Comparator.comparingDouble(SemanticBoundary::score).reversed()
                        .thenComparing(SemanticBoundary::id))
                .limit(safeLimit)
                .toList();
    }

    int size() {
        return examples.size();
    }

    int boundarySize() {
        return boundaries.size();
    }

    private double similarity(
            String query,
            Set<String> queryGrams,
            String candidate,
            Set<String> candidateGrams
    ) {
        Set<String> intersection = new LinkedHashSet<>(queryGrams);
        intersection.retainAll(candidateGrams);
        Set<String> union = new LinkedHashSet<>(queryGrams);
        union.addAll(candidateGrams);
        double jaccard = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
        double containment = queryGrams.isEmpty() ? 0.0 : (double) intersection.size() / queryGrams.size();
        double substring = candidate.contains(query) || query.contains(candidate) ? 1.0 : 0.0;
        return Math.min(1.0, jaccard * 0.55 + containment * 0.35 + substring * 0.10);
    }

    private List<CorpusExample> loadExamples(JsonNode root) {
        if (!root.path("examples").isArray()) {
            return List.of();
        }
        List<CorpusExample> loaded = new ArrayList<>();
        root.path("examples").forEach(node -> {
            String utterance = node.path("utterance").asText("").trim();
            String normalized = normalize(utterance);
            if (utterance.isBlank() || normalized.isBlank()) {
                return;
            }
            Map<String, Object> entities = new LinkedHashMap<>();
            node.path("entities").properties().forEach(entry ->
                    entities.put(entry.getKey(), scalarValue(entry.getValue()))
            );
            loaded.add(new CorpusExample(
                    node.path("id").asText(""),
                    utterance,
                    node.path("intentId").asText("fallback"),
                    node.path("operation").asText("UNKNOWN"),
                    Map.copyOf(entities),
                    normalized,
                    grams(normalized)
            ));
        });
        return List.copyOf(loaded);
    }

    private List<CorpusBoundary> loadBoundaries(JsonNode root) {
        if (!root.path("boundaries").isArray()) {
            return List.of();
        }
        List<CorpusBoundary> loaded = new ArrayList<>();
        root.path("boundaries").forEach(node -> {
            String utterance = node.path("utterance").asText("").trim();
            String normalized = normalize(utterance);
            if (!utterance.isBlank() && !normalized.isBlank()) {
                loaded.add(new CorpusBoundary(
                        node.path("id").asText(""),
                        utterance,
                        node.path("reason").asText("当前请求包含尚未支持的组合规则。"),
                        normalized,
                        grams(normalized)
                ));
            }
        });
        return List.copyOf(loaded);
    }

    private Object scalarValue(JsonNode node) {
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

    private static String normalize(String value) {
        return (value == null ? "" : value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s，。,.!?！？:：;；\\\"'“”‘’`()\\[\\]{}《》<>/\\\\_-]+", "")
                .trim();
    }

    private static Set<String> grams(String normalized) {
        if (normalized.isBlank()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        int[] points = normalized.codePoints().toArray();
        if (points.length == 1) {
            values.add(normalized);
            return Set.copyOf(values);
        }
        for (int index = 0; index < points.length - 1; index++) {
            values.add(new String(points, index, 2));
        }
        return Set.copyOf(values);
    }

    private record CorpusExample(
            String id,
            String utterance,
            String intentId,
            String operation,
            Map<String, Object> entities,
            String normalized,
            Set<String> grams
    ) {
        SemanticExample toResult(double score) {
            return new SemanticExample(id, utterance, intentId, operation, entities, score);
        }
    }

    private record CorpusBoundary(
            String id,
            String utterance,
            String reason,
            String normalized,
            Set<String> grams
    ) {
        SemanticBoundary toResult(double score) {
            return new SemanticBoundary(id, utterance, reason, score);
        }
    }
}
