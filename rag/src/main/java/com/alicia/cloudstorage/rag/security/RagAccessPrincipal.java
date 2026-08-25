package com.alicia.cloudstorage.rag.security;

import java.util.Locale;

public record RagAccessPrincipal(
        Long userId,
        String appCode,
        String role
) {

    public static final String APP_CODE = "rag";
    public static final String ROLE_USER = "RAG_USER";
    public static final String ROLE_ADMIN = "RAG_ADMIN";

    public boolean isRagAdmin() {
        return ROLE_ADMIN.equals(normalizeRole(role));
    }

    private static String normalizeRole(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
