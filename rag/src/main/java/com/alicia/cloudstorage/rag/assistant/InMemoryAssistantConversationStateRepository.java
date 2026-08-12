package com.alicia.cloudstorage.rag.assistant;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryAssistantConversationStateRepository implements AssistantConversationStateRepository {

    private final ConcurrentHashMap<String, AssistantConversationState> conversations = new ConcurrentHashMap<>();

    @Override
    public Optional<AssistantConversationState> find(String conversationId) {
        return Optional.ofNullable(conversations.get(conversationId));
    }

    @Override
    public void save(AssistantConversationState state) {
        conversations.put(state.conversationId(), state);
    }

    @Override
    public void purgeExpired(Instant now) {
        conversations.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    @Override
    public void trimToSize(int maxConversations) {
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
