package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticExampleRetrievalEvaluationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CorpusSemanticExampleRetriever legacy = new CorpusSemanticExampleRetriever(new RagConfigLoader(json));

    @Test
    void lexicalFusionEvaluatesAgainstFrozenSyntheticLabels() throws Exception {
        var cases = frozenCases();
        var baseline = metrics(cases, legacy::retrieve);
        var lexical = metrics(cases, hybrid("")::retrieve);
        System.out.printf("Example retrieval for %d cases: legacy %s, lexical %s%n", cases.size(), baseline, lexical);
        assertThat(lexical.hitAtFive()).isGreaterThanOrEqualTo(baseline.hitAtFive());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ALICIA_TEST_RAG_EMBEDDING_URL", matches = "http://127\\.0\\.0\\.1:[0-9]+/?")
    void realOllamaHybridImprovesTopFiveOnFrozenSyntheticLabels() throws Exception {
        var cases = frozenCases();
        String url = URI.create(System.getenv("ALICIA_TEST_RAG_EMBEDDING_URL")).toString();
        var hybrid = hybrid(url);
        try {
            long started = System.nanoTime();
            assertThat(hybrid.buildIndex()).as("complete embedding index").isTrue();
            long warmupMs = (System.nanoTime() - started) / 1_000_000;
            var baseline = metrics(cases, legacy::retrieve);
            var lexical = metrics(cases, hybrid("")::retrieve);
            var mixed = metrics(cases, hybrid::retrieve);
            System.out.printf("Example retrieval for %d cases (index %d ms): legacy %s, lexical %s, hybrid %s%n",
                    cases.size(), warmupMs, baseline, lexical, mixed);
            assertThat(mixed.hitAtFive()).isGreaterThan(baseline.hitAtFive());
            assertThat(mixed.hitAtFive()).isGreaterThanOrEqualTo(lexical.hitAtFive());
            assertThat(mixed.mrrAtFive()).isGreaterThan(lexical.mrrAtFive());
        } finally {
            hybrid.close();
        }
    }

    private PromptSemanticExampleRetriever hybrid(String url) {
        return new PromptSemanticExampleRetriever(legacy, json, PromptSemanticExampleRetriever.Mode.HYBRID,
                url, "granite-embedding:278m", 350);
    }

    private List<EvaluationCase> frozenCases() throws Exception {
        try (var input = getClass().getResourceAsStream("/recognition/example-retrieval-check.json")) {
            String dataset = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(dataset.getBytes(StandardCharsets.UTF_8));
            assertThat(HexFormat.of().formatHex(hash))
                    .isEqualTo("c7a17566e6baeef2321ab74be0b07de444c741540ac1451d5f8d2100ebef191b");
            var cases = json.readValue(dataset, Dataset.class).cases();
            var corpusText = legacy.catalog().stream().map(SemanticExampleRetriever.SemanticExample::utterance).toList();
            assertThat(cases).hasSize(36).allSatisfy(item -> assertThat(corpusText).doesNotContain(item.query()));
            return cases;
        }
    }

    private static Metrics metrics(List<EvaluationCase> cases,
                                   BiFunction<String, Integer, List<SemanticExampleRetriever.SemanticExample>> retrieve) {
        int firstHits = 0;
        int fiveHits = 0;
        double reciprocal = 0;
        List<Long> micros = new ArrayList<>();
        for (EvaluationCase item : cases) {
            long started = System.nanoTime();
            var matches = retrieve.apply(item.query(), 5);
            micros.add((System.nanoTime() - started) / 1_000);
            for (int index = 0; index < matches.size(); index++) {
                var example = matches.get(index);
                if (item.intent().equals(example.intentId()) && item.operation().equals(example.operation())) {
                    firstHits += index == 0 ? 1 : 0;
                    fiveHits++;
                    reciprocal += 1.0 / (index + 1);
                    break;
                }
            }
        }
        micros.sort(null);
        long p95 = micros.get((int) Math.ceil(micros.size() * 0.95) - 1);
        return new Metrics(firstHits, fiveHits, Math.round(1000 * reciprocal / cases.size()) / 1000.0, p95);
    }

    private record Dataset(String version, List<EvaluationCase> cases) {}
    private record EvaluationCase(String query, String intent, String operation) {}
    private record Metrics(int hitAtOne, int hitAtFive, double mrrAtFive, long p95Micros) {}
}
