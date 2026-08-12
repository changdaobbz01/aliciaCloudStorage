package com.alicia.cloudstorage.rag.assistant;

import java.time.Instant;
import java.util.Optional;

public interface AssistantConversationStateRepository {

    Optional<AssistantConversationState> find(String conversationId);

    void save(AssistantConversationState state);

    void purgeExpired(Instant now);

    void trimToSize(int maxConversations);
}
