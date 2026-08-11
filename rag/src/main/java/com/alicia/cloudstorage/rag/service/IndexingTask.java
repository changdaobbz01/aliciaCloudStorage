package com.alicia.cloudstorage.rag.service;

import java.time.Instant;

public record IndexingTask(
        String eventId,
        Long ownerId,
        Long nodeId,
        boolean includeDescendants,
        Instant occurredAt
) {
}
