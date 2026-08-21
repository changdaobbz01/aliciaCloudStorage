package com.alicia.cloudstorage.identity.dto;

import java.time.LocalDateTime;

public record IdentityAuditLogResponse(
        Long id,
        String eventType,
        String outcome,
        Long actorUserId,
        Long targetUserId,
        String identifier,
        String detail,
        LocalDateTime createdAt
) {
}
