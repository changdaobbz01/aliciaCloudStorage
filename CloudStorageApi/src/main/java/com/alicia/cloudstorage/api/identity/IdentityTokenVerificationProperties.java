package com.alicia.cloudstorage.api.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class IdentityTokenVerificationProperties {

    private static final long MIN_CACHE_SECONDS = 1L;

    private final boolean preflightEnabled;
    private final String issuer;
    private final String audience;
    private final long jwksCacheSeconds;

    public IdentityTokenVerificationProperties(
            @Value("${alicia.identity-token.preflight-enabled:true}") boolean preflightEnabled,
            @Value("${alicia.identity-token.issuer}") String issuer,
            @Value("${alicia.identity-token.audience}") String audience,
            @Value("${alicia.identity-token.jwks-cache-seconds:300}") long jwksCacheSeconds
    ) {
        this.preflightEnabled = preflightEnabled;
        this.issuer = requireText(issuer, "Identity token issuer must be configured.");
        this.audience = requireText(audience, "Identity token audience must be configured.");
        this.jwksCacheSeconds = Math.max(MIN_CACHE_SECONDS, jwksCacheSeconds);
    }

    public boolean preflightEnabled() {
        return preflightEnabled;
    }

    public String issuer() {
        return issuer;
    }

    public String audience() {
        return audience;
    }

    public Duration jwksCacheTtl() {
        return Duration.ofSeconds(jwksCacheSeconds);
    }

    private static String requireText(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }

        return value.trim();
    }
}
