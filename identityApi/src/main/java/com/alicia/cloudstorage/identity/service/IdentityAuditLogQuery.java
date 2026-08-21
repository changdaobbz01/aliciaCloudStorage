package com.alicia.cloudstorage.identity.service;

import java.time.LocalDateTime;

public record IdentityAuditLogQuery(
        String eventType,
        String outcome,
        Long actorUserId,
        Long targetUserId,
        String identifier,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        Integer page,
        Integer size
) {
}
