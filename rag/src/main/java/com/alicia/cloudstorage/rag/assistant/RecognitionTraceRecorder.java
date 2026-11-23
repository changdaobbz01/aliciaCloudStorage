package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
final class RecognitionTraceRecorder {

    private static final int CAPACITY = 256;

    private final boolean enabled;
    private final Set<String> intents;
    private final Set<String> corpusIds;
    private final LinkedHashMap<UUID, RecognitionTrace.Snapshot> completed = new LinkedHashMap<>();

    @Autowired
    RecognitionTraceRecorder(@Value("${alicia.rag.recognition.trace.enabled:false}") boolean enabled,
                             RagConfigLoader configs) {
        this(enabled,
                enabled ? ids(configs.loadJson("rag/conversation/intents.json").path("intents")) : Set.of(),
                enabled ? ids(configs.loadJson("rag/corpus/cloud_drive_semantic_examples.json").path("examples")) : Set.of());
    }

    private RecognitionTraceRecorder(boolean enabled, Set<String> intents, Set<String> corpusIds) {
        this.enabled = enabled;
        this.intents = intents;
        this.corpusIds = corpusIds;
    }

    static RecognitionTraceRecorder disabled() {
        return new RecognitionTraceRecorder(false, Set.of(), Set.of());
    }

    RecognitionTrace start(RecognitionTrace.Route route) {
        return enabled ? new RecognitionTrace(route, this) : null;
    }

    String intent(Object value) {
        return value instanceof String label && intents.contains(label) ? label : "";
    }

    boolean isCorpusId(String value) {
        return value != null && corpusIds.contains(value);
    }

    synchronized void save(RecognitionTrace.Snapshot snapshot) {
        completed.put(snapshot.recognitionTraceId(), snapshot);
        if (completed.size() > CAPACITY) {
            completed.pollFirstEntry();
        }
    }

    synchronized Optional<RecognitionTrace.Snapshot> find(UUID id) {
        return Optional.ofNullable(completed.get(id));
    }

    private static Set<String> ids(JsonNode entries) {
        return StreamSupport.stream(entries.spliterator(), false)
                .map(entry -> entry.path("id").asText(""))
                .filter(id -> !id.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
