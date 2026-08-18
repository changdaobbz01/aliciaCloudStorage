package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.UserRole;

public record StorageQuotaAccount(
        Long userId,
        UserRole role,
        Long storageQuotaBytes
) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public long quotaBytesOrDefault(long defaultQuotaBytes) {
        return storageQuotaBytes == null ? defaultQuotaBytes : storageQuotaBytes;
    }
}
