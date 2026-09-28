package com.alicia.cloudstorage.ragexecution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("alicia.rag-execution.cloud-api")
public record RagExecutionCloudProperties(
        String baseUrl,
        String serviceSecret,
        Duration connectTimeout,
        Duration readTimeout
) {

    public RagExecutionCloudProperties {
        baseUrl = normalizeBaseUrl(baseUrl);
        serviceSecret = serviceSecret == null ? "" : serviceSecret.trim();
        connectTimeout = requirePositive(connectTimeout, "connectTimeout");
        readTimeout = requirePositive(readTimeout, "readTimeout");
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        try {
            URI uri = URI.create(normalized);
            if (!uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("Cloud API base URL must be an absolute http(s) URL.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Cloud API base URL must be an absolute http(s) URL.", exception);
        }
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static Duration requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
        return value;
    }
}
