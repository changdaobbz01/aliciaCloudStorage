package com.alicia.cloudstorage.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "旧密码不能为空。")
        String oldPassword,
        @NotBlank(message = "新密码不能为空。")
        String newPassword
) {
}
