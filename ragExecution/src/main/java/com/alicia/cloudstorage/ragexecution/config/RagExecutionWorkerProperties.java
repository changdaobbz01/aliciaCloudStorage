package com.alicia.cloudstorage.ragexecution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("alicia.rag-execution.worker")
public record RagExecutionWorkerProperties(
        Duration pollDelay,
        Duration streamTimeout,
        Duration streamPollInterval
) {

    public RagExecutionWorkerProperties {
        pollDelay = positive(pollDelay, "pollDelay");
        streamTimeout = positive(streamTimeout, "streamTimeout");
        streamPollInterval = positive(streamPollInterval, "streamPollInterval");
        if (streamTimeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("streamTimeout must not exceed 2 minutes.");
        }
        if (streamPollInterval.compareTo(streamTimeout) >= 0) {
            throw new IllegalArgumentException("streamPollInterval must be shorter than streamTimeout.");
        }
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
        return value;
    }
}
