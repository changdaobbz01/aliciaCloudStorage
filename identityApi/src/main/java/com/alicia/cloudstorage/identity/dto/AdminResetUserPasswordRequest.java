package com.alicia.cloudstorage.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminResetUserPasswordRequest(
        @NotBlank(message = "新密码不能为空。")
        String newPassword
) {
}
