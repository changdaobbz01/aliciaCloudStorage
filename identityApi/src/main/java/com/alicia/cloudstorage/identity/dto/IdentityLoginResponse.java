package com.alicia.cloudstorage.identity.dto;

public record IdentityLoginResponse(
        String token,
        IdentityUserResponse user
) {
}
