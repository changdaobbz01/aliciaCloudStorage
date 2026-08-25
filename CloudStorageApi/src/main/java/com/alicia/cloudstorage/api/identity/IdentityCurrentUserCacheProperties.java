package com.alicia.cloudstorage.api.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class IdentityCurrentUserCacheProperties {

    private static final long MAX_TTL_SECONDS = 30L;

    private final boolean enabled;
    private final long ttlSeconds;
    private final int maxEntries;

    public IdentityCurrentUserCacheProperties(
            @Value("${alicia.identity-current-user-cache.enabled:true}") boolean enabled,
            @Value("${alicia.identity-current-user-cache.ttl-seconds:3}") long ttlSeconds,
            @Value("${alicia.identity-current-user-cache.max-entries:1024}") int maxEntries
    ) {
        this.enabled = enabled && ttlSeconds > 0L && maxEntries > 0;
        this.ttlSeconds = Math.min(MAX_TTL_SECONDS, Math.max(0L, ttlSeconds));
        this.maxEntries = Math.max(0, maxEntries);
    }

    public boolean enabled() {
        return enabled;
    }

    public Duration ttl() {
        return Duration.ofSeconds(ttlSeconds);
    }

    public int maxEntries() {
        return maxEntries;
    }
}
