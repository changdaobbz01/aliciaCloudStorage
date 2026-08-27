package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.service.StorageCommandService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

final class DownloadResponseBuilder {

    private static final String ACCEPT_RANGES_VALUE = "bytes";

    private DownloadResponseBuilder() {
    }

    static ResponseEntity<InputStreamResource> buildFileDownload(
            StorageCommandService.StorageDownloadPayload downloadPayload,
            String cacheControl,
            String varyHeader
    ) {
        MediaType mediaType = DownloadResponseMediaTypes.resolve(downloadPayload.contentType());
        ResponseEntity.BodyBuilder responseBuilder = downloadPayload.partialContent()
                ? ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                : ResponseEntity.ok();

        responseBuilder
                .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                .header(HttpHeaders.VARY, varyHeader)
                .header(HttpHeaders.ACCEPT_RANGES, ACCEPT_RANGES_VALUE)
                .contentType(mediaType)
                .contentLength(downloadPayload.contentLength())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(downloadPayload.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                );

        if (downloadPayload.partialContent()) {
            responseBuilder.header(
                    HttpHeaders.CONTENT_RANGE,
                    downloadPayload.range().toContentRangeHeader(downloadPayload.totalLength())
            );
        }

        return responseBuilder.body(new InputStreamResource(downloadPayload.inputStream()));
    }
}
