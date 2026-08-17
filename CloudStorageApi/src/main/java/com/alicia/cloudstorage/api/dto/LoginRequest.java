package com.alicia.cloudstorage.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        String identifier,
        String phoneNumber,
        String email,
        @NotBlank(message = "密码不能为空。")
        String password
) {
}
