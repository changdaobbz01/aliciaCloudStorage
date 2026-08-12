package com.alicia.cloudstorage.rag.assistant;

import java.util.Map;
import java.util.Optional;

public interface IntentModelClient {

    Optional<ModelIntentResult> recognize(String message);

    default Optional<ModelIntentResult> recognize(IntentModelRequest request) {
        return recognize(request == null ? "" : request.message());
    }

    record IntentModelRequest(
            String message,
            Map<String, Object> context
    ) {
        public IntentModelRequest {
            message = message == null ? "" : message;
            context = context == null ? Map.of() : Map.copyOf(context);
        }
    }

    record ModelIntentResult(
            String provider,
            String model,
            String templateId,
            String promptVersion,
            Map<String, Object> payload
    ) {
    }
}
