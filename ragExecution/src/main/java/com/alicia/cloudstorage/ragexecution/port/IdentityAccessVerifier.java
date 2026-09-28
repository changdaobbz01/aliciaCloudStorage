package com.alicia.cloudstorage.ragexecution.port;

public interface IdentityAccessVerifier {

    IdentityPrincipal requireRagAccess(String authorizationHeader);

    record IdentityPrincipal(long userId, String ragRole) {
        public IdentityPrincipal {
            if (userId <= 0) {
                throw new IllegalArgumentException("userId must be positive.");
            }
            if (ragRole == null || ragRole.isBlank()) {
                throw new IllegalArgumentException("ragRole is required.");
            }
        }
    }
}
