package com.alicia.cloudstorage.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record IdentityLoginRequest(
        String identifier,
        String phoneNumber,
        String email,
        @NotBlank(message = "密码不能为空。")
        String password
) {
}
