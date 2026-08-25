package com.alicia.cloudstorage.rag.health;

import com.alicia.cloudstorage.rag.assistant.StorageApiNodeReadClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RagStorageDependencyHealthService {

    private static final Logger log = LoggerFactory.getLogger(RagStorageDependencyHealthService.class);

    private final StorageApiNodeReadClient storageApiNodeReadClient;

    public RagStorageDependencyHealthService(StorageApiNodeReadClient storageApiNodeReadClient) {
        this.storageApiNodeReadClient = storageApiNodeReadClient;
    }

    public RagDependencyHealth check() {
        if (!storageApiNodeReadClient.isConfigured()) {
            return RagDependencyHealth.notConfigured(
                    "alicia-cloud-storage-api",
                    storageApiNodeReadClient.dependencyOperations()
            );
        }

        try {
            StorageApiHealthProbe probe = storageApiNodeReadClient.checkHealth();
            if (probe.available()) {
                return RagDependencyHealth.available(probe.service(), storageApiNodeReadClient.dependencyOperations());
            }
        } catch (RuntimeException ex) {
            log.warn("RAG Storage dependency health check failed: {}", ex.getMessage());
        }

        return RagDependencyHealth.unavailable(
                "alicia-cloud-storage-api",
                storageApiNodeReadClient.dependencyOperations()
        );
    }
}
