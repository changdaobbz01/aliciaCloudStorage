package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.auth.AuthRequestAttributes;
import com.alicia.cloudstorage.api.auth.CurrentPrincipal;
import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.service.AdminCloudUserCreationService;
import com.alicia.cloudstorage.api.service.AdminCloudUserDirectoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminCloudUserController {

    private final AdminCloudUserCreationService adminCloudUserCreationService;
    private final AdminCloudUserDirectoryService adminCloudUserDirectoryService;

    /**
     * 保留云盘聚合用户管理入口；纯身份写操作直接走 identityApi。
     */
    public AdminCloudUserController(
            AdminCloudUserCreationService adminCloudUserCreationService,
            AdminCloudUserDirectoryService adminCloudUserDirectoryService
    ) {
        this.adminCloudUserCreationService = adminCloudUserCreationService;
        this.adminCloudUserDirectoryService = adminCloudUserDirectoryService;
    }

    @GetMapping
    public List<UserProfileResponse> listUsers(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return adminCloudUserDirectoryService.listUsers(authorization);
    }

    @PostMapping
    public UserProfileResponse createUser(
            @RequestAttribute(AuthRequestAttributes.CURRENT_PRINCIPAL) CurrentPrincipal principal,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody AdminCreateUserRequest request
    ) {
        return adminCloudUserCreationService.createUser(authorization, principal.userId(), request);
    }
}
