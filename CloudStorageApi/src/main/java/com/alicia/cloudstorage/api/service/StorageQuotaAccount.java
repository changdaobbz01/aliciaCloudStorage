package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.identity.UserRole;

import java.util.Map;

public record StorageQuotaAccount(
        Long userId,
        UserRole role,
        Map<String, String> appRoles,
        Long storageQuotaBytes
) {

    private static final String CLOUD_APP_CODE = "cloud";
    private static final String CLOUD_ADMIN_ROLE = "CLOUD_ADMIN";

    public boolean isAdmin() {
        return role == UserRole.ADMIN || CLOUD_ADMIN_ROLE.equals(appRoles().get(CLOUD_APP_CODE));
    }

    public long quotaBytesOrDefault(long defaultQuotaBytes) {
        return storageQuotaBytes == null ? defaultQuotaBytes : storageQuotaBytes;
    }

    public StorageQuotaAccount(Long userId, UserRole role, Long storageQuotaBytes) {
        this(userId, role, Map.of(), storageQuotaBytes);
    }

    public StorageQuotaAccount {
        appRoles = appRoles == null ? Map.of() : Map.copyOf(appRoles);
    }
}
