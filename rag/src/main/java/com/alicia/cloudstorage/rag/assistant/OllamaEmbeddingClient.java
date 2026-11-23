package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class OllamaEmbeddingClient {
    private static final int MAX_DIMENSIONS = 4096;
    private final URI endpoint;
    private final String model;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(350))
            .proxy(HttpClient.Builder.NO_PROXY)
            .build();

    OllamaEmbeddingClient(String baseUrl, String model, ObjectMapper json) {
        URI base;
        try {
            base = URI.create(baseUrl);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid RAG embedding base URL.");
        }
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !List.of("", "/").contains(base.getPath()) || model.isBlank()
                || model.endsWith(":cloud") || model.endsWith("-cloud")) {
            throw new IllegalArgumentException("RAG embeddings require an Ollama base URL and a local model.");
        }
        this.endpoint = base.resolve("/api/embed");
        this.model = model;
        this.json = json;
    }

    List<float[]> encode(List<String> inputs, Duration timeout) {
        CompletableFuture<HttpResponse<byte[]>> exchange = null;
        try {
            byte[] body = json.writeValueAsBytes(Map.of("model", model, "input", inputs, "truncate", false));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            exchange = http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
            HttpResponse<byte[]> response = exchange.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                return List.of();
            }
            JsonNode decoded = json.readTree(response.body());
            return decoded == null ? List.of() : vectors(decoded.path("embeddings"), inputs.size());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (IOException | ExecutionException | TimeoutException error) {
            return List.of();
        } finally {
            if (exchange != null && !exchange.isDone()) {
                exchange.cancel(true);
            }
        }
    }

    private static List<float[]> vectors(JsonNode rows, int expected) {
        if (!rows.isArray() || rows.size() != expected || expected == 0) {
            return List.of();
        }
        int dimensions = rows.get(0).size();
        if (dimensions == 0 || dimensions > MAX_DIMENSIONS) {
            return List.of();
        }
        List<float[]> result = new ArrayList<>(expected);
        for (JsonNode row : rows) {
            float[] vector = unitVector(row, dimensions);
            if (vector == null) {
                return List.of();
            }
            result.add(vector);
        }
        return List.copyOf(result);
    }

    private static float[] unitVector(JsonNode row, int dimensions) {
        if (!row.isArray() || row.size() != dimensions) {
            return null;
        }
        float[] vector = new float[dimensions];
        double magnitude = 0;
        for (int index = 0; index < dimensions; index++) {
            JsonNode number = row.get(index);
            if (!number.isNumber() || !Float.isFinite(number.floatValue())) {
                return null;
            }
            vector[index] = number.floatValue();
            magnitude += (double) vector[index] * vector[index];
        }
        if (magnitude == 0) {
            return null;
        }
        double norm = Math.sqrt(magnitude);
        for (int index = 0; index < dimensions; index++) {
            vector[index] /= norm;
        }
        return vector;
    }
}
