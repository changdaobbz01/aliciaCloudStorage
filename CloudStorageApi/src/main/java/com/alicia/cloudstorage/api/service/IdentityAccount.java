package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.SysUser;
import com.alicia.cloudstorage.api.entity.UserRole;
import com.alicia.cloudstorage.api.entity.UserStatus;

import java.time.LocalDateTime;

public record IdentityAccount(
        Long id,
        String phoneNumber,
        String email,
        String nickname,
        String avatarUrl,
        UserRole role,
        UserStatus status,
        LocalDateTime createdAt
) {

    public static IdentityAccount from(SysUser user) {
        return new IdentityAccount(
                user.getId(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }

    public String phoneNumberOrEmpty() {
        return phoneNumber == null ? "" : phoneNumber;
    }
}
