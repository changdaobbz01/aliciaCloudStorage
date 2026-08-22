package com.alicia.cloudstorage.api.identity;

public record IdentityDependencyHealth(
        boolean available,
        String status,
        String service
) {

    public static IdentityDependencyHealth available(String service) {
        return new IdentityDependencyHealth(true, "ok", normalizeService(service));
    }

    public static IdentityDependencyHealth unavailable() {
        return new IdentityDependencyHealth(false, "unavailable", "alicia-identity-api");
    }

    private static String normalizeService(String service) {
        return service == null || service.isBlank() ? "alicia-identity-api" : service.trim();
    }
}
