package com.alicia.cloudstorage.api.principal;

import com.alicia.cloudstorage.api.identity.UserRole;

import java.util.Map;

public record CurrentPrincipal(
        Long userId,
        UserRole role,
        Map<String, String> appRoles
) {

    private static final String CLOUD_APP_CODE = "cloud";
    private static final String CLOUD_ADMIN_ROLE = "CLOUD_ADMIN";

    public boolean isAdmin() {
        return isCloudAdmin();
    }

    public boolean isCloudAdmin() {
        return role == UserRole.ADMIN || CLOUD_ADMIN_ROLE.equals(appRoles().get(CLOUD_APP_CODE));
    }

    public CurrentPrincipal(Long userId, UserRole role) {
        this(userId, role, Map.of());
    }

    public CurrentPrincipal {
        appRoles = appRoles == null ? Map.of() : Map.copyOf(appRoles);
    }
}
