package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CorpusLocalFallbackEvaluationTest {

    @Test
    void heldOutSupportedCorpusMeetsOfflineRecognitionFloor() {
        RagConfigLoader configLoader = new RagConfigLoader(new ObjectMapper());
        IntentRecognitionService service = offlineService(configLoader);
        JsonNode examples = configLoader.loadJson("rag/corpus/cloud_drive_semantic_eval.json").path("examples");
        int supported = 0;
        int correct = 0;
        List<String> mismatches = new ArrayList<>();

        for (JsonNode example : examples) {
            if (!"RECOGNIZE".equals(example.path("expectedDisposition").asText())) {
                continue;
            }
            supported++;
            String utterance = example.path("utterance").asText();
            String expected = example.path("intentId").asText();
            String actual = service.recognize(utterance).intentId();
            if (expected.equals(actual)) {
                correct++;
            } else if (mismatches.size() < 40) {
                mismatches.add(example.path("id").asText() + ": " + expected + " -> " + actual + " | " + utterance);
            }
        }

        double accuracy = supported == 0 ? 0.0 : (double) correct / supported;
        assertThat(accuracy)
                .withFailMessage("Offline held-out accuracy %.2f%% (%d/%d). Examples: %s",
                        accuracy * 100.0, correct, supported, mismatches)
                .isEqualTo(1.0);
    }

    @Test
    void unsupportedCorpusNeverProducesExecutableDraftOffline() {
        RagConfigLoader configLoader = new RagConfigLoader(new ObjectMapper());
        IntentRecognitionService service = offlineService(configLoader);
        JsonNode examples = configLoader.loadJson("rag/corpus/cloud_drive_semantic_eval.json").path("examples");
        int unsupported = 0;
        List<String> unsafe = new ArrayList<>();

        for (JsonNode example : examples) {
            if (!"CLARIFY_UNSUPPORTED_RULE".equals(example.path("expectedDisposition").asText())) {
                continue;
            }
            unsupported++;
            IntentRecognitionResponse response = service.recognize(example.path("utterance").asText());
            if ((!"fallback".equals(response.intentId())
                    || response.actionDraft().needsBackendBinding()) && unsafe.size() < 20) {
                unsafe.add(example.path("id").asText()
                        + ": " + response.intentId()
                        + " | " + example.path("utterance").asText());
            }
        }

        assertThat(unsupported).isEqualTo(449);
        assertThat(unsafe)
                .withFailMessage("Unsupported corpus produced executable drafts: %s", unsafe)
                .isEmpty();
    }

    private IntentRecognitionService offlineService(RagConfigLoader configLoader) {
        IntentRouter router = new IntentRouter(configLoader, new EntityExtractor(configLoader));
        return new IntentRecognitionService(message -> Optional.empty(), router, configLoader);
    }
}
