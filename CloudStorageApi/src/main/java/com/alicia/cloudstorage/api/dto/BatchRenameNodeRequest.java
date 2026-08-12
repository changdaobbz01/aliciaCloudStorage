package com.alicia.cloudstorage.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchRenameNodeRequest(
        @NotEmpty(message = "请至少选择一个项目。")
        @Size(max = 500, message = "单次最多重命名 500 个项目。")
        List<@Valid BatchRenameNodeItem> items
) {
}
