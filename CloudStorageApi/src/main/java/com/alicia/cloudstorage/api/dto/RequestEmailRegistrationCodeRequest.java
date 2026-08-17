package com.alicia.cloudstorage.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestEmailRegistrationCodeRequest(
        @NotBlank(message = "邮箱不能为空。")
        @Email(message = "请输入有效邮箱地址。")
        @Size(max = 320, message = "邮箱长度不能超过 320 个字符。")
        String email
) {
}
