package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PromptSemanticExampleRetrieverTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CorpusSemanticExampleRetriever legacy = new CorpusSemanticExampleRetriever(new RagConfigLoader(json));

    @Test
    void legacyMatchesExistingScoresAndOrder() {
        var retriever = new PromptSemanticExampleRetriever(legacy, json, PromptSemanticExampleRetriever.Mode.LEGACY,
                "http://127.0.0.1:1", "test-local-model", 350);

        String query = "把合同.docx改名为最终合同.docx";
        assertThat(retriever.retrieve(query, 5)).isEqualTo(legacy.retrieve(query, 5));
        assertThat(retriever.retrieve(query, 0)).isEmpty();
        assertThat(retriever.retrieve(null, 5)).isEmpty();
    }

    @Test
    void returnsOnlyStaticExamplesAndFallsBackWhenEmbeddingResponseStalls() throws Exception {
        var expected = legacy.catalog().getFirst();
        String query = "玄黄霓彩";
        String oversized = query.repeat(129);
        AtomicBoolean stall = new AtomicBoolean();
        AtomicBoolean unexpectedRequest = new AtomicBoolean();
        AtomicInteger queryCalls = new AtomicInteger();
        CountDownLatch releaseResponse = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/embed", exchange -> {
            try (exchange) {
                JsonNode request = json.readTree(exchange.getRequestBody());
                JsonNode inputs = request.path("input");
                if (!"POST".equals(exchange.getRequestMethod()) || !inputs.isArray()
                        || exchange.getRequestHeaders().getFirst("Authorization") != null
                        || !request.path("model").asText().equals("test-local-model")
                        || !request.path("truncate").isBoolean() || request.path("truncate").asBoolean()) {
                    unexpectedRequest.set(true);
                }
                if (inputs.size() == 1 && inputs.get(0).asText().equals(query)) {
                    queryCalls.incrementAndGet();
                }
                var rows = json.createArrayNode();
                for (JsonNode input : inputs) {
                    boolean target = input.asText().equals(expected.utterance()) || input.asText().equals(query);
                    rows.addArray().add(target ? 1 : 0).add(target ? 0 : 1);
                }
                byte[] response = json.writeValueAsBytes(json.createObjectNode().set("embeddings", rows));
                exchange.sendResponseHeaders(200, response.length);
                if (stall.get()) {
                    try {
                        releaseResponse.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                }
                exchange.getResponseBody().write(response);
            }
        });
        server.start();
        var retriever = hybrid("http://127.0.0.1:" + server.getAddress().getPort());
        try {
            var lexical = hybrid("").retrieve(query, 5);
            assertThat(retriever.retrieve(query, 5)).isEqualTo(lexical);
            assertThat(retriever.buildIndex()).isTrue();
            var result = retriever.retrieve(query, 5);
            assertThat(result.getFirst().id()).isEqualTo(expected.id());
            assertThat(result).allSatisfy(example -> assertThat(example.utterance()).isNotEqualTo(query));
            assertThat(retriever.retrieve(oversized, 5)).isEqualTo(legacy.retrieve(oversized, 5));
            stall.set(true);
            assertThat(retriever.retrieve(query, 5)).isEqualTo(lexical);
            assertThat(retriever.retrieve(query, 5)).isEqualTo(lexical);
            assertThat(queryCalls).hasValue(2);
            assertThat(unexpectedRequest).isFalse();
        } finally {
            releaseResponse.countDown();
            retriever.close();
            server.stop(0);
        }
    }

    @Test
    void lexicalRetrievalPreservesExactMatchesAndHandlesFullWidthInput() {
        var retriever = hybrid("");
        String input = "把合同.docx改名为最终合同.docx";
        String fullWidth = input.replace("docx", "ＤＯＣＸ");
        assertThat(retriever.retrieve(fullWidth, 5)).isNotEmpty();
        assertThat(retriever.retrieve(fullWidth.toUpperCase(Locale.ROOT), 5))
                .isEqualTo(retriever.retrieve(fullWidth, 5));
        String exact = legacy.catalog().getFirst().utterance();
        assertThat(retriever.retrieve(exact, 5).getFirst().id()).isEqualTo(legacy.retrieve(exact, 1).getFirst().id());
    }

    @Test
    void modelRequestUsesHybridPromptExamplesOverHttp() throws Exception {
        var captured = new AtomicReference<JsonNode>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try (exchange) {
                captured.set(json.readTree(exchange.getRequestBody()));
                byte[] response = json.writeValueAsBytes(Map.of("choices", List.of(
                        Map.of("message", Map.of("content", "{\"intent_id\":\"collection_move_by_category\"}"))
                )));
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });
        server.start();
        try {
            var loader = new RagConfigLoader(json) {
                @Override
                public JsonNode loadJson(String path) {
                    JsonNode settings = super.loadJson(path);
                    if ("rag/llm/deepseek.json".equals(path) && settings instanceof ObjectNode object) {
                        object.put("base_url", "http://127.0.0.1:" + server.getAddress().getPort());
                    }
                    return settings;
                }
            };
            String query = "项目下的各种视频全挪到素材库";
            var retriever = hybrid("");
            var examples = retriever.retrieve(query, 5);
            assertThat(examples).isNotEmpty().extracting(SemanticExampleRetriever.SemanticExample::id)
                    .isNotEqualTo(legacy.retrieve(query, 5).stream()
                            .map(SemanticExampleRetriever.SemanticExample::id).toList());
            var client = new DeepSeekIntentClient(loader, json, retriever, "test-local-api-key");
            assertThat(client.recognize(query)).isPresent();
            String prompt = captured.get().path("messages").get(1).path("content").asText();
            assertThat(prompt).contains(query, json.writeValueAsString(examples.stream()
                    .map(SemanticExampleRetriever.SemanticExample::promptPayload).toList()));
        } finally {
            server.stop(0);
        }
    }

    private PromptSemanticExampleRetriever hybrid(String url) {
        return new PromptSemanticExampleRetriever(legacy, json, PromptSemanticExampleRetriever.Mode.HYBRID,
                url, "test-local-model", 350);
    }
}
