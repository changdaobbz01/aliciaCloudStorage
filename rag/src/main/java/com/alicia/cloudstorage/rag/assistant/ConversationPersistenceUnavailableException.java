package com.alicia.cloudstorage.rag.assistant;

final class ConversationPersistenceUnavailableException extends RuntimeException {

    ConversationPersistenceUnavailableException() {
        super("Conversation persistence is unavailable.");
    }
}
