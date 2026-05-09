package com.alicia.cloudstorage.api.dto;

public record SignedUrlResponse(
        String url,
        String fileName,
        String contentType,
        long expiresAtEpochMillis
) {
}
