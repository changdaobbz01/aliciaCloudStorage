package com.alicia.cloudstorage.rag.assistant;

import java.util.Map;
import java.util.Optional;

public interface IntentModelClient {

    Optional<ModelIntentResult> recognize(String message);

    record ModelIntentResult(
            String provider,
            String model,
            String templateId,
            String promptVersion,
            Map<String, Object> payload
    ) {
    }
}
