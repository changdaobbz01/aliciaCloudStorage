package com.alicia.cloudstorage.rag.controller;

import com.alicia.cloudstorage.rag.assistant.DeepSeekIntentClient;
import com.alicia.cloudstorage.rag.assistant.StorageApiNodeReadClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final DeepSeekIntentClient deepSeekIntentClient;
    private final StorageApiNodeReadClient storageApiNodeReadClient;

    public HealthController(
            DeepSeekIntentClient deepSeekIntentClient,
            StorageApiNodeReadClient storageApiNodeReadClient
    ) {
        this.deepSeekIntentClient = deepSeekIntentClient;
        this.storageApiNodeReadClient = storageApiNodeReadClient;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "ok");
        status.put("service", "rag-service");
        status.put("deepseekConfigured", deepSeekIntentClient.isConfigured());
        status.put("deepseekModel", deepSeekIntentClient.configuredModel());
        status.put("storageApiConfigured", storageApiNodeReadClient.isConfigured());
        return Map.copyOf(status);
    }
}
