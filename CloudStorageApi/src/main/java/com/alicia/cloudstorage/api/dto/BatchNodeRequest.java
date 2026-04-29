package com.alicia.cloudstorage.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BatchNodeRequest(
        @NotEmpty(message = "请至少选择一个项目。")
        List<@NotNull(message = "项目编号不能为空。") Long> nodeIds
) {
}
