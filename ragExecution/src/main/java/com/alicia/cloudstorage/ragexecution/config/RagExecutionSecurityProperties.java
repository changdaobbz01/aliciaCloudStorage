package com.alicia.cloudstorage.ragexecution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("alicia.rag-execution.security")
public record RagExecutionSecurityProperties(
        String serviceSecret,
        Duration maximumClockSkew,
        Duration nonceTtl
) {
    public RagExecutionSecurityProperties {
        serviceSecret = serviceSecret == null ? "" : serviceSecret.trim();
        maximumClockSkew = positive(maximumClockSkew, "maximumClockSkew");
        nonceTtl = positive(nonceTtl, "nonceTtl");
        if (nonceTtl.compareTo(maximumClockSkew.multipliedBy(2)) < 0) {
            throw new IllegalArgumentException("nonceTtl must cover both sides of the clock-skew window.");
        }
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
        return value;
    }
}
