package com.alicia.cloudstorage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @NotBlank(message = "手机号不能为空。")
        @Pattern(regexp = "^1\\d{10}$", message = "请输入 11 位手机号。")
        String phoneNumber,
        @NotBlank(message = "密码不能为空。")
        String password
) {
}
