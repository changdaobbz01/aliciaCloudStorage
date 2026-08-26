package com.alicia.cloudstorage.api.controller;

import com.alicia.cloudstorage.api.dto.AdminCloudShareLinkResponse;
import com.alicia.cloudstorage.api.dto.AdminCloudOperationsOverviewResponse;
import com.alicia.cloudstorage.api.dto.AdminCloudStorageUserUsageResponse;
import com.alicia.cloudstorage.api.dto.AdminCloudTrashNodeResponse;
import com.alicia.cloudstorage.api.dto.PageResponse;
import com.alicia.cloudstorage.api.service.AdminCloudOperationsDetailService;
import com.alicia.cloudstorage.api.service.AdminCloudOperationsOverviewService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/cloud-operations")
public class AdminCloudOperationsController {

    private final AdminCloudOperationsOverviewService adminCloudOperationsOverviewService;
    private final AdminCloudOperationsDetailService adminCloudOperationsDetailService;

    public AdminCloudOperationsController(
            AdminCloudOperationsOverviewService adminCloudOperationsOverviewService,
            AdminCloudOperationsDetailService adminCloudOperationsDetailService
    ) {
        this.adminCloudOperationsOverviewService = adminCloudOperationsOverviewService;
        this.adminCloudOperationsDetailService = adminCloudOperationsDetailService;
    }

    @GetMapping("/overview")
    public AdminCloudOperationsOverviewResponse getOverview() {
        return adminCloudOperationsOverviewService.getOverview();
    }

    @GetMapping("/shares")
    public PageResponse<AdminCloudShareLinkResponse> listShareLinks(
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean passwordProtected,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection
    ) {
        return adminCloudOperationsDetailService.listShareLinks(
                ownerId,
                status,
                passwordProtected,
                page,
                size,
                sortBy,
                sortDirection
        );
    }

    @GetMapping("/trash")
    public PageResponse<AdminCloudTrashNodeResponse> listTrashNodes(
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean rootOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection
    ) {
        return adminCloudOperationsDetailService.listTrashNodes(
                ownerId,
                keyword,
                type,
                rootOnly,
                page,
                size,
                sortBy,
                sortDirection
        );
    }

    @GetMapping("/users/storage")
    public PageResponse<AdminCloudStorageUserUsageResponse> listStorageUsers(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection
    ) {
        return adminCloudOperationsDetailService.listStorageUsers(
                authorization,
                page,
                size,
                sortBy,
                sortDirection
        );
    }
}
