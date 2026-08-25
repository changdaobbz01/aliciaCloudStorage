package com.alicia.cloudstorage.rag.health;

public record StorageApiHealthProbe(
        boolean available,
        String status,
        String service
) {

    public static StorageApiHealthProbe available(String service) {
        return new StorageApiHealthProbe(true, "ok", normalizeService(service));
    }

    public static StorageApiHealthProbe unavailable(String service) {
        return new StorageApiHealthProbe(false, "unavailable", normalizeService(service));
    }

    public static StorageApiHealthProbe notConfigured() {
        return new StorageApiHealthProbe(false, "not_configured", "alicia-cloud-storage-api");
    }

    private static String normalizeService(String service) {
        return service == null || service.isBlank() ? "alicia-cloud-storage-api" : service.trim();
    }
}
