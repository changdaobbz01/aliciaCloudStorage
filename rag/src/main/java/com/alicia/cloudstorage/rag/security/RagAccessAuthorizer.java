package com.alicia.cloudstorage.rag.security;

public interface RagAccessAuthorizer {

    RagAccessPrincipal requireRagAccess(String authorizationHeader);

    static RagAccessAuthorizer allowAll() {
        return authorizationHeader -> new RagAccessPrincipal(0L, "rag", "RAG_USER");
    }
}
