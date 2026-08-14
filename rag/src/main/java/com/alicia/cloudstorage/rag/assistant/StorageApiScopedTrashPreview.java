package com.alicia.cloudstorage.rag.assistant;

import java.util.List;

record StorageApiScopedTrashPreview(
        List<CandidateItem> items,
        int selectedFileCount,
        int selectedFolderCount,
        int descendantCount,
        int impactCount,
        String scopeFingerprint,
        String impactFingerprint,
        boolean executable,
        String message
) {
    StorageApiScopedTrashPreview {
        items = items == null ? List.of() : List.copyOf(items);
        scopeFingerprint = scopeFingerprint == null ? "" : scopeFingerprint;
        impactFingerprint = impactFingerprint == null ? "" : impactFingerprint;
        message = message == null ? "" : message;
    }
}
