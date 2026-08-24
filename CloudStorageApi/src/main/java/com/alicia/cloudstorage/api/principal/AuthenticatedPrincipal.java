package com.alicia.cloudstorage.api.principal;

import com.alicia.cloudstorage.api.identity.IdentityUserSnapshot;

public record AuthenticatedPrincipal(
        CurrentPrincipal principal,
        IdentityUserSnapshot identityUser
) {
}
