package com.alicia.cloudstorage.api.dto;

import java.util.List;

public record SaveShareLinkRequest(
        Long parentId,
        List<Long> selectedNodeIds
) {
}
