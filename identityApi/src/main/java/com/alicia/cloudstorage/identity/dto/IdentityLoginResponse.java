package com.alicia.cloudstorage.identity.dto;

public record IdentityLoginResponse(
        String token,
        String refreshToken,
        IdentityUserResponse user
) {
}
