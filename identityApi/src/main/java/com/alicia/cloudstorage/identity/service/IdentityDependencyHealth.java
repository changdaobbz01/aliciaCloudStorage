package com.alicia.cloudstorage.identity.service;

public record IdentityDependencyHealth(
        boolean available,
        IdentityDependencyCheck database,
        IdentityFlywayDependencyCheck flyway
) {

    public static IdentityDependencyHealth of(
            IdentityDependencyCheck database,
            IdentityFlywayDependencyCheck flyway
    ) {
        return new IdentityDependencyHealth(database.available() && flyway.available(), database, flyway);
    }
}
