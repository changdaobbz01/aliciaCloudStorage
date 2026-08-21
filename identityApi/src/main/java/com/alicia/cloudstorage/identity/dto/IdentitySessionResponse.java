package com.alicia.cloudstorage.identity.dto;

import java.time.LocalDateTime;

public record IdentitySessionResponse(
        Long id,
        LocalDateTime issuedAt,
        LocalDateTime lastUsedAt,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt,
        String revokeReason,
        String clientIp,
        String userAgent,
        boolean current
) {
}
