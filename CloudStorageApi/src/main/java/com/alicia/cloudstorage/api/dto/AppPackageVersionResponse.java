package com.alicia.cloudstorage.api.dto;

import java.time.LocalDateTime;

public record AppPackageVersionResponse(
        boolean available,
        String versionName,
        String releaseNotes,
        String downloadUrl,
        LocalDateTime uploadedAt
) {
}
