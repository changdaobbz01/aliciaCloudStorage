package com.alicia.cloudstorage.identity.dto;

import java.time.OffsetDateTime;

public record IdentityErrorResponse(
        int status,
        String error,
        OffsetDateTime timestamp
) {
}
