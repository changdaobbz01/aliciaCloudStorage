package com.alicia.cloudstorage.ragexecution.api.publicapi;

import java.time.Instant;

public record ExecutionEventResponse(
        long sequence,
        String type,
        Object payload,
        Instant createdAt
) {
}
