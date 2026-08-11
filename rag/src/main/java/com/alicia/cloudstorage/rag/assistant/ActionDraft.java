package com.alicia.cloudstorage.rag.assistant;

import java.util.Map;

public record ActionDraft(
        String type,
        Map<String, Object> parameters,
        boolean needsBackendBinding
) {
}
