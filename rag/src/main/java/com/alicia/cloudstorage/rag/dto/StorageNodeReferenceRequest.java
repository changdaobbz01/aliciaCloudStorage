package com.alicia.cloudstorage.rag.dto;

import jakarta.validation.constraints.NotNull;

public record StorageNodeReferenceRequest(
        @NotNull Long ownerId,
        @NotNull Long nodeId
) {
}
