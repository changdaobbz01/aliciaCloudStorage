package com.alicia.cloudstorage.api.identity;

public interface IdentityUserGateway {

    IdentityUserSnapshot getUser(Long userId);
}
