package com.alicia.cloudstorage.ragexecution.api.publicapi;

import java.util.List;

public record CompleteClientInputRequest(
        Long expectedVersion,
        String stepId,
        String status,
        List<Long> nodeIds,
        String errorCode
) {
}
