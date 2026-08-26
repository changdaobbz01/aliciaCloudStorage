package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.dto.AdminCloudOperationsOverviewResponse;
import com.alicia.cloudstorage.api.service.AdminCloudOperationsOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/cloud-operations")
public class AdminCloudOperationsController {

    private final AdminCloudOperationsOverviewService adminCloudOperationsOverviewService;

    public AdminCloudOperationsController(
            AdminCloudOperationsOverviewService adminCloudOperationsOverviewService
    ) {
        this.adminCloudOperationsOverviewService = adminCloudOperationsOverviewService;
    }

    @GetMapping("/overview")
    public AdminCloudOperationsOverviewResponse getOverview() {
        return adminCloudOperationsOverviewService.getOverview();
    }
}
