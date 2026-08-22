package com.alicia.cloudstorage.api.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class IdentityApiClientProperties {

    private static final long MIN_TIMEOUT_MS = 1L;

    private final String baseUrl;
    private final long connectTimeoutMs;
    private final long readTimeoutMs;

    public IdentityApiClientProperties(
            @Value("${alicia.identity-api.base-url:http://localhost:8093}") String baseUrl,
            @Value("${alicia.identity-api.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${alicia.identity-api.read-timeout-ms:5000}") long readTimeoutMs
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
            throw new IllegalArgumentException("Identity API base URL must be configured.");
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static long normalizeTimeoutMs(long timeoutMs) {
        return Math.max(MIN_TIMEOUT_MS, timeoutMs);
    }
}
