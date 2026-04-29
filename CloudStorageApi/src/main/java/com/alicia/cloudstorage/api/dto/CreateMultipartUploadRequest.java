package com.alicia.cloudstorage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateMultipartUploadRequest(
        Long parentId,

        @NotBlank(message = "文件名不能为空。")
        String fileName,

        @NotNull(message = "文件大小不能为空。")
        @Positive(message = "文件大小必须大于 0。")
        Long fileSize,

        String contentType,

        @NotNull(message = "分片大小不能为空。")
        @Positive(message = "分片大小必须大于 0。")
        Long chunkSize,

        @NotNull(message = "分片数量不能为空。")
        @Positive(message = "分片数量必须大于 0。")
        Integer totalChunks,

        @NotBlank(message = "文件指纹不能为空。")
        String fingerprint
) {
}
