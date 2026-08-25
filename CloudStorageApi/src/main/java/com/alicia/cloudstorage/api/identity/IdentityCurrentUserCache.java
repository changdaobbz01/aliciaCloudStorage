package com.alicia.cloudstorage.api.identity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

@Service
public class IdentityCurrentUserCache {

    private final boolean enabled;
    private final long ttlMillis;
    private final int maxEntries;
    private final LongSupplier clockMillis;
    private final ConcurrentMap<String, CacheEntry> entries = new ConcurrentHashMap<>();

    @Autowired
    public IdentityCurrentUserCache(IdentityCurrentUserCacheProperties properties) {
        this(properties.enabled(), properties.ttl(), properties.maxEntries(), System::currentTimeMillis);
    }

    IdentityCurrentUserCache(
            boolean enabled,
            Duration ttl,
            int maxEntries,
            LongSupplier clockMillis
    ) {
        this.enabled = enabled && !ttl.isZero() && !ttl.isNegative() && maxEntries > 0;
        this.ttlMillis = Math.max(0L, ttl.toMillis());
        this.maxEntries = Math.max(0, maxEntries);
        this.clockMillis = clockMillis;
    }

    public Optional<IdentityUserSnapshot> get(String authorization) {
        if (!enabled) {
            return Optional.empty();
        }

        String key = fingerprint(authorization);
        CacheEntry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }

        long now = clockMillis.getAsLong();
        if (entry.expiresAtMillis() <= now) {
            entries.remove(key, entry);
            return Optional.empty();
        }

        return Optional.of(entry.snapshot());
    }

    public void put(String authorization, IdentityUserSnapshot snapshot) {
        if (!enabled || snapshot == null) {
            return;
        }

        if (entries.size() >= maxEntries) {
            evictExpiredOrOneOldest();
        }

        long expiresAtMillis = clockMillis.getAsLong() + ttlMillis;
        entries.put(fingerprint(authorization), new CacheEntry(snapshot, expiresAtMillis));
    }

    public void invalidate(String authorization) {
        if (!enabled) {
            return;
        }

        entries.remove(fingerprint(authorization));
    }

    int size() {
        return entries.size();
    }

    private void evictExpiredOrOneOldest() {
        long now = clockMillis.getAsLong();
        String oldestKey = null;
        long oldestExpiresAt = Long.MAX_VALUE;

        for (var entry : entries.entrySet()) {
            long expiresAt = entry.getValue().expiresAtMillis();
            if (expiresAt <= now) {
                entries.remove(entry.getKey(), entry.getValue());
                continue;
            }
            if (expiresAt < oldestExpiresAt) {
                oldestExpiresAt = expiresAt;
                oldestKey = entry.getKey();
            }
        }

        if (entries.size() >= maxEntries && oldestKey != null) {
            entries.remove(oldestKey);
        }
    }

    private static String fingerprint(String authorization) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.valueOf(authorization).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", ex);
        }
    }

    private record CacheEntry(
            IdentityUserSnapshot snapshot,
            long expiresAtMillis
    ) {
    }
}
