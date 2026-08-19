package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.identity.dto.IdentityMessageResponse;
import com.alicia.cloudstorage.identity.service.IdentityAdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/identity/admin/users")
public class IdentityAdminUserController {

    private final IdentityAdminUserService identityAdminUserService;

    public IdentityAdminUserController(IdentityAdminUserService identityAdminUserService) {
        this.identityAdminUserService = identityAdminUserService;
    }

    @PutMapping("/{userId}/password")
    public IdentityMessageResponse resetUserPassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long userId,
            @Valid @RequestBody AdminResetUserPasswordRequest request
    ) {
        identityAdminUserService.resetUserPassword(authorization, userId, request);
        return new IdentityMessageResponse("用户密码已重置，旧登录状态已失效。");
    }
}
