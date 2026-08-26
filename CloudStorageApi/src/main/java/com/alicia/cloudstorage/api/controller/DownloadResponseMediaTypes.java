package com.alicia.cloudstorage.api.controller;

import org.springframework.http.MediaType;

final class DownloadResponseMediaTypes {

    private DownloadResponseMediaTypes() {
    }

    static MediaType resolve(String rawContentType) {
        if (rawContentType == null || rawContentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            return MediaType.parseMediaType(rawContentType.trim());
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
