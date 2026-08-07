package com.alicia.cloudstorage.api.service;

import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

public interface AppPackageStorage {

    StoredAppPackage store(MultipartFile file, String fileName);

    AppPackageDownloadLink createDownloadLink(String objectKey, String fileName, String contentType);

    void deleteObjectQuietly(String objectKey);

    record StoredAppPackage(
            String objectKey,
            String contentType,
            long fileSizeBytes
    ) {
    }

    record AppPackageDownloadLink(
            URI uri,
            long expiresAtEpochMillis
    ) {
    }
}
