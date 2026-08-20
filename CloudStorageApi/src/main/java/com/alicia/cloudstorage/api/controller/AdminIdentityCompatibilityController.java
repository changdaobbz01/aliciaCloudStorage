package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.auth.AuthRequestAttributes;
import com.alicia.cloudstorage.api.auth.CurrentPrincipal;
import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminResetUserPasswordRequest;
import com.alicia.cloudstorage.api.dto.ApiMessageResponse;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.service.AdminIdentityCompatibilityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminIdentityCompatibilityController {

    private final AdminIdentityCompatibilityService adminIdentityCompatibilityService;

    /**
     * 保留旧版 /api/admin/users 管理入口，实际身份写入由 identityApi 完成。
     */
    public AdminIdentityCompatibilityController(AdminIdentityCompatibilityService adminIdentityCompatibilityService) {
        this.adminIdentityCompatibilityService = adminIdentityCompatibilityService;
    }

    @GetMapping
    public List<UserProfileResponse> listUsers(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return adminIdentityCompatibilityService.listUsers(authorization);
    }

    @PostMapping
    public UserProfileResponse createUser(
            @RequestAttribute(AuthRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody AdminCreateUserRequest request
    ) {
        return adminIdentityCompatibilityService.createUser(authorization, principal.userId(), request);
    }

    @PutMapping("/{userId}/password")
    public ApiMessageResponse resetUserPassword(
            @RequestAttribute(AuthRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable Long userId,
            @Valid @RequestBody AdminResetUserPasswordRequest request
    ) {
        adminIdentityCompatibilityService.resetUserPassword(authorization, userId, request);
        return new ApiMessageResponse("用户密码已重置，旧登录状态已失效。");
    }
}
