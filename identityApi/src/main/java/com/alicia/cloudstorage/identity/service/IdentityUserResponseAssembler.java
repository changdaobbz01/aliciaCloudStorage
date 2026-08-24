package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.entity.IdentityUser;
import org.springframework.stereotype.Service;

@Service
public class IdentityUserResponseAssembler {

    private final IdentityApplicationRoleService identityApplicationRoleService;

    public IdentityUserResponseAssembler(IdentityApplicationRoleService identityApplicationRoleService) {
        this.identityApplicationRoleService = identityApplicationRoleService;
    }

    public IdentityUserResponse toResponse(IdentityUser user) {
        return IdentityUserResponse.from(user, identityApplicationRoleService.effectiveRolesForUser(user));
    }
}
