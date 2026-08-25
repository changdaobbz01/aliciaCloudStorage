package com.alicia.cloudstorage.rag.health;

import java.util.List;

public record RagDependencyHealth(
        boolean available,
        String status,
        String service,
        List<RagDependencyOperationSnapshot> operations
) {

    public static RagDependencyHealth available(
            String service,
            List<RagDependencyOperationSnapshot> operations
    ) {
        return new RagDependencyHealth(
                true,
                "ok",
                normalizeService(service),
                List.copyOf(operations)
        );
    }

    public static RagDependencyHealth unavailable(
            String service,
            List<RagDependencyOperationSnapshot> operations
    ) {
        return new RagDependencyHealth(
                false,
                "unavailable",
                normalizeService(service),
                List.copyOf(operations)
        );
    }

    public static RagDependencyHealth notConfigured(
            String service,
            List<RagDependencyOperationSnapshot> operations
    ) {
        return new RagDependencyHealth(
                false,
                "not_configured",
                normalizeService(service),
                List.copyOf(operations)
        );
    }

    private static String normalizeService(String service) {
        return service == null || service.isBlank() ? "unknown" : service.trim();
    }
}
