package com.alicia.cloudstorage.identity.service;

public record IdentityDependencyCheck(
        boolean available,
        String status
) {

    public static IdentityDependencyCheck ok() {
        return new IdentityDependencyCheck(true, "ok");
    }

    public static IdentityDependencyCheck unavailable() {
        return new IdentityDependencyCheck(false, "unavailable");
    }
}
