package com.alicia.cloudstorage.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifySharePasswordRequest(
        @NotBlank(message = "请输入分享提取码。")
        String password
) {
}
