package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class DeepSeekAssistantReplyPolisher implements AssistantReplyPolisher {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAssistantReplyPolisher.class);

    private final RagConfigLoader configLoader;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;

    public DeepSeekAssistantReplyPolisher(
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
    public Optional<String> polish(PolishRequest request) {
        DeepSeekReplySettings settings = loadSettings();
        String resolvedApiKey = resolvedApiKey(settings);
        if (!settings.enabled() || resolvedApiKey.isBlank() || request.templateText().isBlank()) {
            log.debug("DeepSeek reply polish skipped. enabled={}, apiKeyConfigured={}", settings.enabled(), !resolvedApiKey.isBlank());
            return Optional.empty();
        }

        try {
            String body = objectMapper.writeValueAsString(buildRequestBody(settings, request));
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(settings.baseUrl().replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + resolvedApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .orTimeout(settings.timeoutSeconds(), TimeUnit.SECONDS)
                    .join();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("DeepSeek reply polish returned HTTP status {}", response.statusCode());
                return Optional.empty();
            }
            return parseResponse(response.body());
        } catch (JsonProcessingException exception) {
            log.warn("DeepSeek reply polish returned an invalid JSON payload: {}", exception.getOriginalMessage());
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.warn("DeepSeek reply polish failed: {}", exception.toString());
            return Optional.empty();
        }
    }

    private Map<String, Object> buildRequestBody(DeepSeekReplySettings settings, PolishRequest request) {
        String userContent = TextSupport.safeFormat(settings.userTemplate(), Map.of(
                "user_message", request.userMessage(),
                "intent_id", request.intentId(),
                "intent_name", request.intentName(),
                "task_type", request.taskType(),
                "next_action", request.nextAction(),
                "action_type", request.actionType(),
                "risk", request.risk(),
                "requires_confirmation", request.requiresConfirmation(),
                "missing_slots", String.join(", ", request.missingSlots()),
                "template_text", request.templateText()
        ));
        return Map.of(
                "model", settings.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", settings.systemMessage()),
                        Map.of("role", "user", "content", userContent)
                ),
                "temperature", settings.temperature(),
                "max_tokens", settings.maxTokens(),
                "stream", false,
                "response_format", Map.of("type", settings.responseFormat())
        );
    }

    private Optional<String> parseResponse(String body) throws JsonProcessingException {
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
        return Optional.ofNullable(payload.get("assistant_text"))
                .map(String::valueOf)
                .map(String::trim)
                .filter(text -> !text.isBlank());
    }

    private DeepSeekReplySettings loadSettings() {
        JsonNode root = configLoader.loadJson("rag/llm/deepseek_reply.json");
        JsonNode prompt = root.path("prompt");
        return new DeepSeekReplySettings(
                root.path("enabled").asBoolean(false),
                root.path("provider").asText("deepseek"),
                root.path("base_url").asText("https://api.deepseek.com"),
                root.path("api_key_env").asText("DEEPSEEK_API_KEY"),
                root.path("model").asText("deepseek-v4-flash"),
                root.path("temperature").asDouble(0.65),
                root.path("max_tokens").asInt(600),
                root.path("timeout_seconds").asInt(20),
                root.path("response_format").asText("json_object"),
                prompt.path("system_message").asText(),
                prompt.path("user_template").asText()
        );
    }

    private String resolvedApiKey(DeepSeekReplySettings settings) {
        if (!apiKey.isBlank()) {
            return apiKey;
        }
        String envName = settings.apiKeyEnv();
        return envName == null || envName.isBlank() ? "" : System.getenv().getOrDefault(envName, "").trim();
    }

    private String stripJsonContent(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    private record DeepSeekReplySettings(
            boolean enabled,
            String provider,
            String baseUrl,
            String apiKeyEnv,
            String model,
            double temperature,
            int maxTokens,
            int timeoutSeconds,
            String responseFormat,
            String systemMessage,
            String userTemplate
    ) {
    }
}
