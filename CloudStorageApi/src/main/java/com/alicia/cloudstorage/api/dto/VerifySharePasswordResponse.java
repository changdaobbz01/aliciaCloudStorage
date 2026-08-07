package com.alicia.cloudstorage.api.dto;

import java.time.LocalDateTime;

public record VerifySharePasswordResponse(
        String accessToken,
        LocalDateTime expiresAt
) {
}
