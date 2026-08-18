package com.alicia.cloudstorage.api.service;

public record IdentityLoginSession(
        String token,
        IdentityAccount account
) {
}
