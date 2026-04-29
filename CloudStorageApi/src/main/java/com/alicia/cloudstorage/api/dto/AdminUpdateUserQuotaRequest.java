package com.alicia.cloudstorage.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminUpdateUserQuotaRequest(
        @NotNull(message = "最大存储额度不能为空。")
        @Positive(message = "最大存储额度必须大于 0。")
        Long storageQuotaBytes
) {
}
