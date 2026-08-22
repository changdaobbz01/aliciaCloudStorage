package com.alicia.cloudstorage.identity.service;

public record IdentityFlywayDependencyCheck(
        boolean available,
        String status,
        String historyTable,
        String latestVersion
) {

    private static final String HISTORY_TABLE = "identity_flyway_schema_history";

    public static IdentityFlywayDependencyCheck ok(String latestVersion) {
        return new IdentityFlywayDependencyCheck(true, "ok", HISTORY_TABLE, normalizeLatestVersion(latestVersion));
    }

    public static IdentityFlywayDependencyCheck unavailable() {
        return new IdentityFlywayDependencyCheck(false, "unavailable", HISTORY_TABLE, null);
    }

    private static String normalizeLatestVersion(String latestVersion) {
        return latestVersion == null || latestVersion.isBlank() ? "unknown" : latestVersion.trim();
    }
}
