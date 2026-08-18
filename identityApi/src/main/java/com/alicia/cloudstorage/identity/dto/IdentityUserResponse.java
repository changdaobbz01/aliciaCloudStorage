package com.alicia.cloudstorage.identity.dto;

import com.alicia.cloudstorage.identity.entity.IdentityUser;

import java.time.LocalDateTime;

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
        LocalDateTime createdAt
) {

    public static IdentityUserResponse from(IdentityUser user) {
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
                user.getCreatedAt()
        );
    }
}
