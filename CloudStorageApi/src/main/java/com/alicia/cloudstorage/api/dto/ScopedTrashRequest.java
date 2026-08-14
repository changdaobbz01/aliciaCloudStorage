package com.alicia.cloudstorage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ScopedTrashRequest(
        @NotBlank String selectorVersion,
        Long sourceParentId,
        boolean root,
        @NotEmpty List<@NotBlank String> nodeTypes,
        @NotEmpty @Size(max = 500) List<@NotNull Long> nodeIds,
        @NotBlank String scopeFingerprint,
        @NotBlank String impactFingerprint,
        @NotNull Integer expectedImpactCount
) {
}
