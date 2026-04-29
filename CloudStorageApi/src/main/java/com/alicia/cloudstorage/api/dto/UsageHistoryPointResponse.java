package com.alicia.cloudstorage.api.dto;

import java.time.LocalDate;

public record UsageHistoryPointResponse(
        LocalDate date,
        long usedBytes
) {
}
