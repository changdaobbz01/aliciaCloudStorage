package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.dto.AdminCreateIdentityUserRequest;
import com.alicia.cloudstorage.identity.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.identity.dto.IdentityMessageResponse;
import com.alicia.cloudstorage.identity.dto.IdentityUserResponse;
import com.alicia.cloudstorage.identity.service.IdentityAdminUserService;
import com.alicia.cloudstorage.identity.service.IdentityPasswordService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/identity/admin/users")
public class IdentityAdminUserController {

    private final IdentityAdminUserService identityAdminUserService;
    private final IdentityPasswordService identityPasswordService;

    public IdentityAdminUserController(
            IdentityAdminUserService identityAdminUserService,
            IdentityPasswordService identityPasswordService
    ) {
        this.identityAdminUserService = identityAdminUserService;
        this.identityPasswordService = identityPasswordService;
    }

    @GetMapping
    public List<IdentityUserResponse> listUsers(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return identityAdminUserService.listUsers(authorization);
    }

    @PostMapping
    public IdentityUserResponse createUser(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody AdminCreateIdentityUserRequest request
    ) {
        return identityAdminUserService.createUser(authorization, request);
    }

    @PutMapping("/{userId}/password")
    public IdentityMessageResponse resetUserPassword(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long userId,
            @Valid @RequestBody AdminResetUserPasswordRequest request
    ) {
        identityPasswordService.resetUserPassword(authorization, userId, request);
        return new IdentityMessageResponse("用户密码已重置，旧登录状态已失效。");
    }
}
