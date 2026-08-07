package com.alicia.cloudstorage.api.dto;

import java.time.LocalDateTime;

public record ShareLinkStatusResponse(
        String shareCode,
        String title,
        boolean available,
        boolean requiresPassword,
        LocalDateTime expiresAt,
        String reason
) {
}
