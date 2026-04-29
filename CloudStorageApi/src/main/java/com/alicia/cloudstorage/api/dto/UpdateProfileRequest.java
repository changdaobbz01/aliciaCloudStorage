package com.alicia.cloudstorage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "手机号不能为空。")
        @Pattern(regexp = "^1\\d{10}$", message = "请输入 11 位手机号。")
        String phoneNumber,
        @NotBlank(message = "昵称不能为空。")
        @Size(max = 100, message = "昵称长度不能超过 100 个字符。")
        String nickname,
        @Size(max = 500, message = "头像地址长度不能超过 500 个字符。")
        String avatarUrl
) {
}
