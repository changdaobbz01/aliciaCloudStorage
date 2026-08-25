package com.alicia.cloudstorage.rag.security;

public record RagAccessPrincipal(
        Long userId,
        String appCode,
        String role
) {
}
