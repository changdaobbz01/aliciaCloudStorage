package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.dto.AdminUpdateUserQuotaRequest;
import com.alicia.cloudstorage.api.dto.UserProfileResponse;
import com.alicia.cloudstorage.api.service.AdminCloudUserProfileManagementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/cloud-users")
public class AdminCloudUserProfileController {

    private final AdminCloudUserProfileManagementService adminCloudUserProfileManagementService;

    public AdminCloudUserProfileController(
            AdminCloudUserProfileManagementService adminCloudUserProfileManagementService
    ) {
        this.adminCloudUserProfileManagementService = adminCloudUserProfileManagementService;
    }

    @PutMapping("/{userId}/quota")
    public UserProfileResponse updateUserQuota(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUpdateUserQuotaRequest request
    ) {
        return adminCloudUserProfileManagementService.updateUserStorageQuota(userId, request);
    }
}
