package com.alicia.cloudstorage.rag.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AssistantConversationStore {

    private final ConcurrentHashMap<String, AssistantConversationState> conversations = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxConversations;

    public AssistantConversationStore(
            @Value("${alicia.rag.conversation.ttl-minutes:30}") long ttlMinutes,
            @Value("${alicia.rag.conversation.max-conversations:500}") int maxConversations
    ) {
        this.ttl = Duration.ofMinutes(Math.max(1L, ttlMinutes));
        this.maxConversations = Math.max(32, maxConversations);
    }

    public AssistantConversationState resolve(String requestedConversationId) {
        Instant now = Instant.now();
        purgeExpired(now);
        String conversationId = normalizeConversationId(requestedConversationId);
        if (!conversationId.isBlank()) {
            AssistantConversationState existing = conversations.get(conversationId);
            if (existing != null && !existing.isExpired(now)) {
                return existing;
            }
        }

        return newConversation(now);
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
                response.candidateBinding(),
                AssistantConversationFocus.next(previous.focus(), response),
                now.plus(ttl)
        );
        conversations.put(next.conversationId(), next);
        return next;
    }

    public AssistantConversationState restart() {
        return newConversation(Instant.now());
    }

    private AssistantConversationState newConversation(Instant now) {
        return new AssistantConversationState(
                UUID.randomUUID().toString(),
                0,
                "",
                Map.of(),
                java.util.List.of(),
                null,
                null,
                AssistantConversationFocus.empty(),
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
        conversations.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private void trimIfNeeded() {
        if (conversations.size() < maxConversations) {
            return;
        }

        conversations.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().expiresAt()))
                .limit(Math.max(1, conversations.size() - maxConversations + 1L))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(conversations::remove);
    }
}
