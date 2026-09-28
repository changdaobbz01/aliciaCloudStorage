package com.alicia.cloudstorage.api.ragexecution.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class RagExecutionCloudActionFeatureGuard {

    private final RagExecutionCloudActionProperties properties;

    public RagExecutionCloudActionFeatureGuard(RagExecutionCloudActionProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        if (properties.enabled() && properties.serviceSecret().length() < 32) {
            throw new IllegalStateException(
                    "Cloud RAG action dispatch requires a dedicated service secret of at least 32 characters."
            );
        }
    }
}
