package com.alicia.cloudstorage.api.dto;

import java.time.LocalDateTime;

public record ApiErrorResponse(
        int status,
        String error,
        LocalDateTime timestamp
) {
}
