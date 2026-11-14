package com.alicia.cloudstorage.ragexecution.api.admin;

import com.alicia.cloudstorage.ragexecution.application.AdminExecutionQueryService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/executions")
public class AdminExecutionController {

    private final AdminExecutionQueryService queryService;

    public AdminExecutionController(AdminExecutionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/overview")
    public AdminExecutionQueryService.Overview overview(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return queryService.overview(authorization);
    }

    @GetMapping
    public AdminExecutionQueryService.PageResult list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return queryService.list(status, ownerUserId, page, size, authorization);
    }

    @GetMapping("/{executionId}")
    public AdminExecutionQueryService.Detail get(
            @PathVariable String executionId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return queryService.get(executionId, authorization);
    }
}
