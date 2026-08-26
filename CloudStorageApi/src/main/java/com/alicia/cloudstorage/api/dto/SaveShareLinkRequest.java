package com.alicia.cloudstorage.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveShareLinkRequest(
        Long parentId,
        @Size(max = 500, message = "单次最多保存 500 个分享项目。")
        List<@NotNull(message = "分享项目编号不能为空。") Long> selectedNodeIds
) {
}
