package com.alicia.cloudstorage.api.dto;

import java.util.List;

public record MultipartUploadStatusResponse(
        String uploadToken,
        String fileName,
        long fileSize,
        String contentType,
        long chunkSize,
        int totalChunks,
        List<MultipartUploadPartResponse> uploadedParts,
        String status
) {
}
