package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
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
    private final SecretKeySpec ownerKey;
    private final LinkedHashMap<UUID, CompletedTrace> completed = new LinkedHashMap<>();

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
        byte[] randomKey = new byte[32];
        if (enabled) {
            new SecureRandom().nextBytes(randomKey);
        }
        this.ownerKey = new SecretKeySpec(randomKey, "HmacSHA256");
    }

    static RecognitionTraceRecorder disabled() {
        return new RecognitionTraceRecorder(false, Set.of(), Set.of());
    }

    RecognitionTrace start(RecognitionTrace.Route route) {
        return start(route, null);
    }

    RecognitionTrace start(RecognitionTrace.Route route, Long userId) {
        return enabled ? new RecognitionTrace(route, this, ownerTag(userId)) : null;
    }

    String intent(Object value) {
        return value instanceof String label && intents.contains(label) ? label : "";
    }

    boolean isCorpusId(String value) {
        return value != null && corpusIds.contains(value);
    }

    synchronized void save(RecognitionTrace.Snapshot snapshot, UUID ownerTag) {
        completed.put(snapshot.recognitionTraceId(), new CompletedTrace(snapshot, ownerTag));
        if (completed.size() > CAPACITY) {
            completed.pollFirstEntry();
        }
    }

    synchronized Optional<RecognitionTrace.Snapshot> find(UUID id) {
        return Optional.ofNullable(completed.get(id)).map(CompletedTrace::snapshot);
    }

    synchronized boolean owns(UUID id, Long userId) {
        CompletedTrace trace = completed.get(id);
        return trace != null && trace.ownerTag() != null && trace.ownerTag().equals(ownerTag(userId));
    }

    private UUID ownerTag(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(ownerKey);
            ByteBuffer tag = ByteBuffer.wrap(mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(userId).array()));
            return new UUID(tag.getLong(), tag.getLong());
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Recognition trace owner verification is unavailable.", error);
        }
    }

    private static Set<String> ids(JsonNode entries) {
        return StreamSupport.stream(entries.spliterator(), false)
                .map(entry -> entry.path("id").asText(""))
                .filter(id -> !id.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private record CompletedTrace(RecognitionTrace.Snapshot snapshot, UUID ownerTag) {}
}
