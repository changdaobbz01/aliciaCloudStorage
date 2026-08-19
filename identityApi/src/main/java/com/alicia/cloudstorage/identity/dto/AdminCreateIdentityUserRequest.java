package com.alicia.cloudstorage.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCreateIdentityUserRequest(
        String phoneNumber,
        String email,
        @NotBlank(message = "昵称不能为空。")
        @Size(max = 100, message = "昵称长度不能超过 100 个字符。")
        String nickname,
        @Size(max = 500, message = "头像地址长度不能超过 500 个字符。")
        String avatarUrl,
        @NotBlank(message = "密码不能为空。")
        String password,
        String role
) {
}
