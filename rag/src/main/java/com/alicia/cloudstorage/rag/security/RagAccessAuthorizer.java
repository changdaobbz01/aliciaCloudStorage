package com.alicia.cloudstorage.rag.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public interface RagAccessAuthorizer {

    RagAccessPrincipal requireRagAccess(String authorizationHeader);

    default RagAccessPrincipal requireRagAdminAccess(String authorizationHeader) {
        RagAccessPrincipal principal = requireRagAccess(authorizationHeader);
        if (principal == null || !principal.isRagAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "RAG admin access is required.");
        }
        return principal;
    }

    static RagAccessAuthorizer allowAll() {
        return allowRole(RagAccessPrincipal.ROLE_USER);
    }

    static RagAccessAuthorizer allowAdmin() {
        return allowRole(RagAccessPrincipal.ROLE_ADMIN);
    }

    static RagAccessAuthorizer allowRole(String role) {
        return authorizationHeader -> new RagAccessPrincipal(0L, RagAccessPrincipal.APP_CODE, role);
    }
}
