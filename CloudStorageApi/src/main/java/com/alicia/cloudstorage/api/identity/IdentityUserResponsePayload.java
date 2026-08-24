package com.alicia.cloudstorage.api.identity;

import java.time.LocalDateTime;
import java.util.Map;

record IdentityUserResponsePayload(
        Long id,
        String phoneNumber,
        String email,
        String emailVerifiedAt,
        String nickname,
        String avatarUrl,
        Long tokenVersion,
        String role,
        String status,
        String createdAt,
        Map<String, String> appRoles
) {

    IdentityUserSnapshot toSnapshot() {
        return new IdentityUserSnapshot(
                id,
                phoneNumber,
                email,
                nickname,
                avatarUrl,
                UserRole.valueOf(role),
                UserStatus.valueOf(status),
                LocalDateTime.parse(createdAt),
                appRoles
        );
    }
}
