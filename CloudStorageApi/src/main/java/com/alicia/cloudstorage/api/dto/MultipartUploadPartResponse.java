package com.alicia.cloudstorage.api.dto;

public record MultipartUploadPartResponse(
        int partNumber,
        String eTag,
        long size
) {
}
