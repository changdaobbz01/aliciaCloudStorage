package com.alicia.cloudstorage.api.identity;

import java.time.LocalDateTime;
import java.util.Map;

public record IdentityUserSnapshot(
        Long id,
        String phoneNumber,
        String email,
        String nickname,
        String avatarUrl,
        UserRole role,
        UserStatus status,
        LocalDateTime createdAt,
        Map<String, String> appRoles
) {

    private static final String CLOUD_APP_CODE = "cloud";
    private static final String CLOUD_ADMIN_ROLE = "CLOUD_ADMIN";

    public String phoneNumberOrEmpty() {
        return phoneNumber == null ? "" : phoneNumber;
    }

    public boolean isCloudAdmin() {
        return role == UserRole.ADMIN || CLOUD_ADMIN_ROLE.equals(appRoles().get(CLOUD_APP_CODE));
    }

    public IdentityUserSnapshot(
            Long id,
            String phoneNumber,
            String email,
            String nickname,
            String avatarUrl,
            UserRole role,
            UserStatus status,
            LocalDateTime createdAt
    ) {
        this(
                id,
                phoneNumber,
                email,
                nickname,
                avatarUrl,
                role,
                status,
                createdAt,
                Map.of()
        );
    }

    public IdentityUserSnapshot {
        appRoles = appRoles == null ? Map.of() : Map.copyOf(appRoles);
    }
}
