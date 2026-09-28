package com.alicia.cloudstorage.ragexecution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("alicia.rag-execution.identity-api")
public record RagExecutionIdentityProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    public RagExecutionIdentityProperties {
        baseUrl = normalizeBaseUrl(baseUrl);
        connectTimeout = positive(connectTimeout, "connectTimeout");
        readTimeout = positive(readTimeout, "readTimeout");
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Identity API base URL must be an absolute http(s) URL.", exception);
        }
        if (uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Identity API base URL must be an absolute http(s) URL.");
        }
        return normalized;
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
        return value;
    }
}
