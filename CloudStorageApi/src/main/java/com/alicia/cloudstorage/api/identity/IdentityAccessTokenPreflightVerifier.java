package com.alicia.cloudstorage.api.identity;

public interface IdentityAccessTokenPreflightVerifier {

    void verify(String authorization);
}
