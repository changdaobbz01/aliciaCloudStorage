package com.alicia.cloudstorage.api.ragexecution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("alicia.rag-execution.internal-actions")
public record RagExecutionCloudActionProperties(
        boolean enabled,
        String serviceSecret,
        Duration maximumClockSkew,
        Duration nonceTtl,
        Duration requestMaxAge,
        int maxRequestBytes
) {
    public RagExecutionCloudActionProperties {
        serviceSecret = serviceSecret == null ? "" : serviceSecret.trim();
        maximumClockSkew = positive(maximumClockSkew, "maximumClockSkew");
        nonceTtl = positive(nonceTtl, "nonceTtl");
        requestMaxAge = positive(requestMaxAge, "requestMaxAge");
        if (nonceTtl.compareTo(maximumClockSkew.multipliedBy(2)) < 0) {
            throw new IllegalArgumentException("nonceTtl must cover both sides of the clock-skew window.");
        }
        if (maxRequestBytes < 1024 || maxRequestBytes > 1_048_576) {
            throw new IllegalArgumentException("maxRequestBytes must be between 1024 and 1048576.");
        }
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
        return value;
    }
}
