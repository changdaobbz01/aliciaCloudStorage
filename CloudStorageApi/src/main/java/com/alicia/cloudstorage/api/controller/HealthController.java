package com.alicia.cloudstorage.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class HealthController {

    /**
     * 返回后端服务健康状态，供前端首页和部署巡检使用。
     */
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "alicia-cloud-storage-api",
                "timestamp", LocalDateTime.now()
        );
    }
}
