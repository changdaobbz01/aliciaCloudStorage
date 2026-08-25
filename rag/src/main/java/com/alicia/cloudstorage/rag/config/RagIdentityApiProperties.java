package com.alicia.cloudstorage.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

@Component
public class RagIdentityApiProperties {

    private static final long MIN_TIMEOUT_MS = 1L;

    private final String baseUrl;
    private final long connectTimeoutMs;
    private final long readTimeoutMs;

    public RagIdentityApiProperties(
            @Value("${alicia.rag.identity-api.base-url:http://localhost:8093}") String baseUrl,
            @Value("${alicia.rag.identity-api.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${alicia.rag.identity-api.read-timeout-ms:5000}") long readTimeoutMs
    ) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.connectTimeoutMs = normalizeTimeoutMs(connectTimeoutMs);
        this.readTimeoutMs = normalizeTimeoutMs(readTimeoutMs);
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Duration connectTimeout() {
        return Duration.ofMillis(connectTimeoutMs);
    }

    public Duration readTimeout() {
        return Duration.ofMillis(readTimeoutMs);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("RAG Identity API base URL must be configured.");
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        validateBaseUrl(normalized);
        return normalized;
    }

    private static void validateBaseUrl(String normalized) {
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("RAG Identity API base URL must be an absolute http(s) URL.", ex);
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("RAG Identity API base URL must use http or https.");
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("RAG Identity API base URL must include a host.");
        }
    }

    private static long normalizeTimeoutMs(long timeoutMs) {
        return Math.max(MIN_TIMEOUT_MS, timeoutMs);
    }
}
