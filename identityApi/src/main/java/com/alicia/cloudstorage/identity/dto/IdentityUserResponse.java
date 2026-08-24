package com.alicia.cloudstorage.identity.dto;

import com.alicia.cloudstorage.identity.entity.IdentityUser;

import java.time.LocalDateTime;
import java.util.Map;

public record IdentityUserResponse(
        Long id,
        String phoneNumber,
        String email,
        LocalDateTime emailVerifiedAt,
        String nickname,
        String avatarUrl,
        Long tokenVersion,
        String role,
        String status,
        LocalDateTime createdAt,
        Map<String, String> appRoles
) {

    public IdentityUserResponse {
        appRoles = appRoles == null ? Map.of() : Map.copyOf(appRoles);
    }

    public static IdentityUserResponse from(IdentityUser user) {
        return from(user, Map.of());
    }

    public static IdentityUserResponse from(IdentityUser user, Map<String, String> appRoles) {
        return new IdentityUserResponse(
                user.getId(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getEmailVerifiedAt(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getTokenVersion(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getCreatedAt(),
                appRoles
        );
    }

    public IdentityUserResponse(
            Long id,
            String phoneNumber,
            String email,
            LocalDateTime emailVerifiedAt,
            String nickname,
            String avatarUrl,
            Long tokenVersion,
            String role,
            String status,
            LocalDateTime createdAt
    ) {
        this(
                id,
                phoneNumber,
                email,
                emailVerifiedAt,
                nickname,
                avatarUrl,
                tokenVersion,
                role,
                status,
                createdAt,
                Map.of()
        );
    }
}
