package com.alicia.cloudstorage.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyEmailRegistrationRequest(
        @NotBlank(message = "邮箱不能为空。")
        @Email(message = "请输入有效邮箱地址。")
        @Size(max = 320, message = "邮箱长度不能超过 320 个字符。")
        String email,
        @NotBlank(message = "验证码不能为空。")
        @Pattern(regexp = "^\\d{6}$", message = "验证码应为 6 位数字。")
        String code,
        @NotBlank(message = "昵称不能为空。")
        @Size(max = 100, message = "昵称长度不能超过 100 个字符。")
        String nickname,
        @NotBlank(message = "密码不能为空。")
        String password
) {
}
