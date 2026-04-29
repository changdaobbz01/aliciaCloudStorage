package com.alicia.cloudstorage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameNodeRequest(
        @NotBlank(message = "名称不能为空。")
        @Size(max = 255, message = "名称长度不能超过 255 个字符。")
        String name
) {
}
