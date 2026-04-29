package com.alicia.cloudstorage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFolderRequest(
        Long parentId,
        @NotBlank(message = "文件夹名称不能为空。")
        @Size(max = 255, message = "文件夹名称长度不能超过 255 个字符。")
        String folderName
) {
}
