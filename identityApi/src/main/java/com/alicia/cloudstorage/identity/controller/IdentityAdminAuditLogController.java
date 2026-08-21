package com.alicia.cloudstorage.identity.controller;

import com.alicia.cloudstorage.identity.dto.IdentityAuditLogPageResponse;
import com.alicia.cloudstorage.identity.service.IdentityAuditLogQuery;
import com.alicia.cloudstorage.identity.service.IdentityAuditLogQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/identity/admin/audit-logs")
public class IdentityAdminAuditLogController {

    private final IdentityAuditLogQueryService identityAuditLogQueryService;

    public IdentityAdminAuditLogController(IdentityAuditLogQueryService identityAuditLogQueryService) {
        this.identityAuditLogQueryService = identityAuditLogQueryService;
    }

    @GetMapping
    public IdentityAuditLogPageResponse listAuditLogs(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false) String identifier,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return identityAuditLogQueryService.listAuditLogs(
                authorization,
                new IdentityAuditLogQuery(
                        eventType,
                        outcome,
                        actorUserId,
                        targetUserId,
                        identifier,
                        createdFrom,
                        createdTo,
                        page,
                        size
                )
        );
    }
}
