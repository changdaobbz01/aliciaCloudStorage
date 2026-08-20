package com.alicia.cloudstorage.api.identity;

public record IdentityLoginSession(
        String token,
        IdentityAccount account
) {
}
