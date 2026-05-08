package com.alicia.cloudstorage.api.dto;

import java.time.LocalDateTime;

public record AppPackageInfoResponse(
        boolean available,
        String fileName,
        Long fileSizeBytes,
        LocalDateTime uploadedAt,
        String downloadUrl
) {
}
