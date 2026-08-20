package com.alicia.cloudstorage.api.identity;

public record IdentityLoginSession(
        String token,
        IdentityUserSnapshot user
) {
}
