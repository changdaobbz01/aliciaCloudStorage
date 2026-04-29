package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.dto.AdminCreateUserRequest;
import com.alicia.cloudstorage.api.dto.AdminUpdateUserQuotaRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.service.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserAccountService userAccountService;

    /**
     * 注入账号业务服务，供管理员账号管理接口使用。
     */
    public AdminUserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    /**
     * 查询管理员可见的全部账号列表。
     */
    @GetMapping
    public List<UserProfileResponse> listUsers() {
        return userAccountService.listUsers();
    }

    /**
     * 由管理员创建新的账号记录。
     */
    @PostMapping
    public UserProfileResponse createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return userAccountService.createUser(request);
    }

    @PutMapping("/{userId}/quota")
    public UserProfileResponse updateUserQuota(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUpdateUserQuotaRequest request
    ) {
        return userAccountService.updateUserStorageQuota(userId, request);
    }
}
