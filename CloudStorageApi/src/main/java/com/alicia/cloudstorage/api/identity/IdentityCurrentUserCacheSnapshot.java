package com.alicia.cloudstorage.api.identity;

public record IdentityCurrentUserCacheSnapshot(
        boolean enabled,
        long ttlMillis,
        int maxEntries,
        int size
) {

    public static IdentityCurrentUserCacheSnapshot disabled() {
        return new IdentityCurrentUserCacheSnapshot(false, 0L, 0, 0);
    }
}
