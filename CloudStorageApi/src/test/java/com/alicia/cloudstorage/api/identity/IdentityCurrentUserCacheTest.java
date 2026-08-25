package com.alicia.cloudstorage.api.identity;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityCurrentUserCacheTest {

    @Test
    void cacheIsDisabledWhenPropertiesDisableIt() {
        IdentityCurrentUserCache cache = new IdentityCurrentUserCache(
                false,
                Duration.ofSeconds(3L),
                128,
                () -> 1000L
        );

        cache.put("Bearer token", account(1L));

        assertThat(cache.get("Bearer token")).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void getRemovesExpiredEntries() {
        AtomicLong now = new AtomicLong(1000L);
        IdentityCurrentUserCache cache = new IdentityCurrentUserCache(
                true,
                Duration.ofMillis(50L),
                128,
                now::get
        );

        cache.put("Bearer token", account(1L));
        now.set(1100L);

        assertThat(cache.get("Bearer token")).isEmpty();
        assertThat(cache.size()).isZero();
    }

    @Test
    void evictsOldestEntryWhenCapacityIsReached() {
        AtomicLong now = new AtomicLong(1000L);
        IdentityCurrentUserCache cache = new IdentityCurrentUserCache(
                true,
                Duration.ofMillis(1000L),
                2,
                now::get
        );

        cache.put("Bearer first", account(1L));
        now.set(1100L);
        cache.put("Bearer second", account(2L));
        now.set(1200L);
        cache.put("Bearer third", account(3L));

        assertThat(cache.get("Bearer first")).isEmpty();
        assertThat(cache.get("Bearer second")).hasValueSatisfying(account ->
                assertThat(account.id()).isEqualTo(2L)
        );
        assertThat(cache.get("Bearer third")).hasValueSatisfying(account ->
                assertThat(account.id()).isEqualTo(3L)
        );
    }

    @Test
    void ttlIsClampedToShortOperationalWindow() {
        IdentityCurrentUserCacheProperties properties =
                new IdentityCurrentUserCacheProperties(true, 120L, 128);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.ttl()).isEqualTo(Duration.ofSeconds(30L));
    }

    private IdentityUserSnapshot account(Long id) {
        return new IdentityUserSnapshot(
                id,
                "13900000000",
                "user@example.com",
                "Alicia",
                null,
                UserRole.USER,
                UserStatus.ACTIVE,
                LocalDateTime.of(2026, 4, 29, 15, 30),
                Map.of("cloud", "CLOUD_USER")
        );
    }
}
