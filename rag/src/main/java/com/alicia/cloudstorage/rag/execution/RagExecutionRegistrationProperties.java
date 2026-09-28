package com.alicia.cloudstorage.rag.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

@Component
public class RagExecutionRegistrationProperties {

    private final boolean enabled;
    private final String baseUrl;
    private final String serviceSecret;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration confirmationTtl;

    public RagExecutionRegistrationProperties(
            @Value("${alicia.rag.execution-registration.enabled:false}") boolean enabled,
            @Value("${alicia.rag.execution-registration.base-url:http://localhost:8094}") String baseUrl,
            @Value("${alicia.rag.execution-registration.service-secret:}") String serviceSecret,
            @Value("${alicia.rag.execution-registration.connect-timeout:2s}") Duration connectTimeout,
            @Value("${alicia.rag.execution-registration.read-timeout:5s}") Duration readTimeout,
            @Value("${alicia.rag.execution-registration.confirmation-ttl:10m}") Duration confirmationTtl
    ) {
        this.enabled = enabled;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.serviceSecret = serviceSecret == null ? "" : serviceSecret.trim();
        this.connectTimeout = positive(connectTimeout, "connectTimeout");
        this.readTimeout = positive(readTimeout, "readTimeout");
        this.confirmationTtl = positive(confirmationTtl, "confirmationTtl");
        if (enabled && this.serviceSecret.length() < 32) {
            throw new IllegalStateException("RAG shadow registration requires a dedicated service secret of at least 32 characters.");
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String serviceSecret() {
        return serviceSecret;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    public Duration confirmationTtl() {
        return confirmationTtl;
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
            throw new IllegalArgumentException("RAG execution base URL must be an absolute http(s) URL.", exception);
        }
        if (uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("RAG execution base URL must be an absolute http(s) URL.");
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
