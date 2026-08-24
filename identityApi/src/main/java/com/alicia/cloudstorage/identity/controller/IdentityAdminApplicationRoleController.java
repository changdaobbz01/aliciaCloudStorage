package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.dto.IdentityApplicationRoleResponse;
import com.alicia.cloudstorage.identity.dto.UpdateIdentityApplicationRoleRequest;
import com.alicia.cloudstorage.identity.service.IdentityApplicationRoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/identity/admin/users/{userId}/app-roles")
public class IdentityAdminApplicationRoleController {

    private final IdentityApplicationRoleService identityApplicationRoleService;

    public IdentityAdminApplicationRoleController(IdentityApplicationRoleService identityApplicationRoleService) {
        this.identityApplicationRoleService = identityApplicationRoleService;
    }

    @GetMapping
    public List<IdentityApplicationRoleResponse> listUserRoles(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long userId
    ) {
        return identityApplicationRoleService.listUserRoles(authorization, userId);
    }

    @PutMapping("/{appCode}")
    public IdentityApplicationRoleResponse updateUserRole(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long userId,
            @PathVariable String appCode,
            @Valid @RequestBody UpdateIdentityApplicationRoleRequest request
    ) {
        return identityApplicationRoleService.updateUserRole(authorization, userId, appCode, request);
    }
}
