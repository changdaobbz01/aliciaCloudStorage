package com.alicia.cloudstorage.api.identity;

public interface IdentityUserGateway {

    IdentityAccount getUser(Long userId);
}
