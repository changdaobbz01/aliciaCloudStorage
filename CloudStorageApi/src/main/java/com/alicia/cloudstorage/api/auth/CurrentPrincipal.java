package com.alicia.cloudstorage.api.auth;

import com.alicia.cloudstorage.api.entity.UserRole;

public record CurrentPrincipal(
        Long userId,
        UserRole role
) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
