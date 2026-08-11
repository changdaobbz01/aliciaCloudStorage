package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class DeepSeekIntentClient implements IntentModelClient {

    private final RagConfigLoader configLoader;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;

    public DeepSeekIntentClient(
            RagConfigLoader configLoader,
            ObjectMapper objectMapper,
            @Value("${alicia.rag.deepseek.api-key:}") String apiKey
    ) {
        this.configLoader = configLoader;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public Optional<ModelIntentResult> recognize(String message) {
        DeepSeekSettings settings = loadSettings();
        if (!settings.enabled() || apiKey.isBlank()) {
            return Optional.empty();
        }

        try {
            String body = objectMapper.writeValueAsString(buildRequestBody(settings, message));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(settings.baseUrl().replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .orTimeout(settings.timeoutSeconds(), TimeUnit.SECONDS)
                    .join();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            return parseResponse(settings, response.body());
        } catch (JsonProcessingException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Map<String, Object> buildRequestBody(DeepSeekSettings settings, String message) {
        return Map.of(
                "model", settings.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", settings.systemMessage()),
                        Map.of("role", "user", "content", settings.userTemplate().replace("{message}", message))
                ),
                "temperature", settings.temperature(),
                "max_tokens", settings.maxTokens(),
                "stream", false,
                "response_format", Map.of("type", settings.responseFormat())
        );
    }

    private Optional<ModelIntentResult> parseResponse(DeepSeekSettings settings, String body) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode choice = root.path("choices").isArray() && !root.path("choices").isEmpty()
                ? root.path("choices").get(0)
                : null;
        if (choice == null) {
            return Optional.empty();
        }
        String content = choice.path("message").path("content").asText("");
        if (content.isBlank()) {
            return Optional.empty();
        }
        Map<String, Object> payload = objectMapper.readValue(stripJsonContent(content), new TypeReference<>() {
        });
        return Optional.of(new ModelIntentResult(
                settings.provider(),
                settings.model(),
                settings.templateId(),
                settings.promptVersion(),
                payload
        ));
    }

    private DeepSeekSettings loadSettings() {
        JsonNode root = configLoader.loadJson("rag/llm/deepseek.json");
        JsonNode prompt = root.path("prompt");
        return new DeepSeekSettings(
                root.path("enabled").asBoolean(false),
                root.path("provider").asText("deepseek"),
                root.path("base_url").asText("https://api.deepseek.com"),
                root.path("model").asText("deepseek-v4-flash"),
                root.path("temperature").asDouble(0.1),
                root.path("max_tokens").asInt(1200),
                root.path("timeout_seconds").asInt(30),
                root.path("response_format").asText("json_object"),
                prompt.path("version").asText("intent_recognition_v1"),
                prompt.path("template_id").asText("deepseek_file_intent_recognition"),
                prompt.path("system_message").asText(),
                prompt.path("user_template").asText()
        );
    }

    private String stripJsonContent(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    private record DeepSeekSettings(
            boolean enabled,
            String provider,
            String baseUrl,
            String model,
            double temperature,
            int maxTokens,
            int timeoutSeconds,
            String responseFormat,
            String promptVersion,
            String templateId,
            String systemMessage,
            String userTemplate
    ) {
    }
}
