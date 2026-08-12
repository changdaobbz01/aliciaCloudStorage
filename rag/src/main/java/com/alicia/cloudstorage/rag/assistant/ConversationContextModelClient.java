package com.alicia.cloudstorage.rag.assistant;

import java.util.Map;
import java.util.Optional;

public interface ConversationContextModelClient {

    Optional<ConversationContextModelResult> resolve(
            String message,
            AssistantConversationState conversation,
            IntentRecognitionResponse baseResponse
    );

    record ConversationContextModelResult(
            String provider,
            String model,
            String templateId,
            Map<String, Object> payload
    ) {
    }
}
