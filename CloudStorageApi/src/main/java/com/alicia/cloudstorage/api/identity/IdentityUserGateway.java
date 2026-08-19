package com.alicia.cloudstorage.api.identity;

import com.alicia.cloudstorage.api.service.IdentityAccount;

public interface IdentityUserGateway {

    IdentityAccount getUser(Long userId);
}
