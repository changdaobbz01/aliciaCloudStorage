package com.alicia.cloudstorage.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateShareLinkRequest(
        @NotEmpty(message = "请选择要分享的文件或文件夹。")
        @Size(max = 20, message = "单个分享最多包含 20 个项目。")
        List<Long> nodeIds,
        String title,
        String password,
        Integer expiresInDays,
        Boolean allowDownload,
        Boolean allowSave
) {
}
