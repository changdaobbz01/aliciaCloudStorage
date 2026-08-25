package com.alicia.cloudstorage.api.identity;

import java.util.List;

public record IdentityDependencyHealth(
        boolean available,
        String status,
        String service,
        IdentityCurrentUserCacheSnapshot currentUserCache,
        List<IdentityGatewayOperationSnapshot> operations
) {

    public static IdentityDependencyHealth available(String service) {
        return available(service, List.of());
    }

    public static IdentityDependencyHealth available(
            String service,
            IdentityCurrentUserCacheSnapshot currentUserCache,
            List<IdentityGatewayOperationSnapshot> operations
    ) {
        return new IdentityDependencyHealth(
                true,
                "ok",
                normalizeService(service),
                normalizeCacheSnapshot(currentUserCache),
                List.copyOf(operations)
        );
    }

    public static IdentityDependencyHealth available(
            String service,
            List<IdentityGatewayOperationSnapshot> operations
    ) {
        return available(service, IdentityCurrentUserCacheSnapshot.disabled(), operations);
    }

    public static IdentityDependencyHealth unavailable() {
        return unavailable(List.of());
    }

    public static IdentityDependencyHealth unavailable(List<IdentityGatewayOperationSnapshot> operations) {
        return unavailable(IdentityCurrentUserCacheSnapshot.disabled(), operations);
    }

    public static IdentityDependencyHealth unavailable(
            IdentityCurrentUserCacheSnapshot currentUserCache,
            List<IdentityGatewayOperationSnapshot> operations
    ) {
        return new IdentityDependencyHealth(
                false,
                "unavailable",
                "alicia-identity-api",
                normalizeCacheSnapshot(currentUserCache),
                List.copyOf(operations)
        );
    }

    private static String normalizeService(String service) {
        return service == null || service.isBlank() ? "alicia-identity-api" : service.trim();
    }

    private static IdentityCurrentUserCacheSnapshot normalizeCacheSnapshot(IdentityCurrentUserCacheSnapshot snapshot) {
        return snapshot == null ? IdentityCurrentUserCacheSnapshot.disabled() : snapshot;
    }
}
