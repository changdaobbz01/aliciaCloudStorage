package com.alicia.cloudstorage.identity.dto;

import java.util.List;

public record IdentityAuditLogPageResponse(
        List<IdentityAuditLogResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
