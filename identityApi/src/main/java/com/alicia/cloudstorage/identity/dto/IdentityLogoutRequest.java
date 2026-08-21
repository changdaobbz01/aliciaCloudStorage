package com.alicia.cloudstorage.identity.dto;

public record IdentityLogoutRequest(
        String refreshToken,
        Boolean allDevices
) {
    public boolean logoutAllDevices() {
        return Boolean.TRUE.equals(allDevices);
    }
}
