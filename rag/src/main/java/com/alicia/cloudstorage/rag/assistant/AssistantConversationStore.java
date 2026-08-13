package com.alicia.cloudstorage.rag.assistant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class AssistantConversationStore {

    private final AssistantConversationStateRepository repository;
    private final Duration ttl;
    private final int maxConversations;

    @Autowired
    public AssistantConversationStore(
            AssistantConversationStateRepository repository,
            @Value("${alicia.rag.conversation.ttl-minutes:30}") long ttlMinutes,
            @Value("${alicia.rag.conversation.max-conversations:500}") int maxConversations
    ) {
        this.repository = repository;
        this.ttl = Duration.ofMinutes(Math.max(1L, ttlMinutes));
        this.maxConversations = Math.max(32, maxConversations);
    }

    public AssistantConversationStore(long ttlMinutes, int maxConversations) {
        this(new InMemoryAssistantConversationStateRepository(), ttlMinutes, maxConversations);
    }

    public AssistantConversationState resolve(String requestedConversationId) {
        return resolve(requestedConversationId, "");
    }

    public AssistantConversationState resolve(String requestedConversationId, String authorizationHeader) {
        Instant now = Instant.now();
        purgeExpired(now);
        String fingerprint = authorizationFingerprint(authorizationHeader);
        String conversationId = normalizeConversationId(requestedConversationId);
        if (!conversationId.isBlank()) {
            AssistantConversationState existing = repository.find(conversationId).orElse(null);
            if (existing != null
                    && !existing.isExpired(now)
                    && existing.authorizationFingerprint().equals(fingerprint)) {
                return existing;
            }
        }

        return newConversation(now, fingerprint);
    }

    public AssistantConversationState save(
            AssistantConversationState previous,
            IntentRecognitionResponse response
    ) {
        Instant now = Instant.now();
        purgeExpired(now);
        trimIfNeeded();
        AssistantConversationState next = new AssistantConversationState(
                previous.conversationId(),
                previous.turnIndex() + 1,
                response.intentId(),
                response.entities(),
                response.missingSlots(),
                response.actionDraft(),
                response.actionPlan(),
                response.candidateBinding(),
                AssistantConversationFocus.next(previous.focus(), response),
                response.semanticFrame(),
                previous.authorizationFingerprint(),
                now.plus(ttl)
        );
        repository.save(next);
        return next;
    }

    public AssistantConversationState restart() {
        return restart("");
    }

    public AssistantConversationState restart(String authorizationHeader) {
        return newConversation(Instant.now(), authorizationFingerprint(authorizationHeader));
    }

    public void complete(String conversationId) {
        complete(conversationId, "");
    }

    public void complete(String conversationId, String authorizationHeader) {
        String normalized = normalizeConversationId(conversationId);
        AssistantConversationState existing = normalized.isBlank()
                ? null
                : repository.find(normalized).orElse(null);
        if (existing != null
                && existing.authorizationFingerprint().equals(authorizationFingerprint(authorizationHeader))) {
            repository.delete(normalized);
        }
    }

    private AssistantConversationState newConversation(Instant now, String authorizationFingerprint) {
        return new AssistantConversationState(
                UUID.randomUUID().toString(),
                0,
                "",
                Map.of(),
                java.util.List.of(),
                null,
                null,
                null,
                AssistantConversationFocus.empty(),
                SemanticFrame.empty(),
                authorizationFingerprint,
                now.plus(ttl)
        );
    }

    private String normalizeConversationId(String conversationId) {
        String value = conversationId == null ? "" : conversationId.trim();
        if (value.length() > 128 || !value.matches("[A-Za-z0-9._-]*")) {
            return "";
        }
        return value;
    }

    private void purgeExpired(Instant now) {
        repository.purgeExpired(now);
    }

    private void trimIfNeeded() {
        repository.trimToSize(maxConversations);
    }

    private String authorizationFingerprint(String authorizationHeader) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((authorizationHeader == null ? "" : authorizationHeader.trim())
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint authorization context.", exception);
        }
    }
}
