package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.CloudObjectCleanupSource;
import com.alicia.cloudstorage.api.entity.CloudObjectCleanupTask;
import com.alicia.cloudstorage.api.repository.CloudObjectCleanupTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
public class CloudObjectCleanupService {

    private static final Logger log = LoggerFactory.getLogger(CloudObjectCleanupService.class);

    private final CloudObjectCleanupTaskRepository cleanupTaskRepository;
    private final CloudObjectCleanupTaskProcessor cleanupTaskProcessor;
    private final Clock clock;
    private final boolean cleanupEnabled;
    private final int batchSize;

    public CloudObjectCleanupService(
            CloudObjectCleanupTaskRepository cleanupTaskRepository,
            CloudObjectCleanupTaskProcessor cleanupTaskProcessor,
            Clock clock,
            @Value("${alicia.cloud-object-cleanup.enabled:true}") boolean cleanupEnabled,
            @Value("${alicia.cloud-object-cleanup.batch-size:100}") int batchSize
    ) {
        this.cleanupTaskRepository = cleanupTaskRepository;
        this.cleanupTaskProcessor = cleanupTaskProcessor;
        this.clock = clock;
        this.cleanupEnabled = cleanupEnabled;
        this.batchSize = Math.max(1, batchSize);
    }

    @Transactional
    public void trackAndDeleteAfterCommit(Collection<String> objectKeys, CloudObjectCleanupSource source) {
        trackAndScheduleDeletion(objectKeys, source);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trackAndDeleteNow(Collection<String> objectKeys, CloudObjectCleanupSource source) {
        trackAndScheduleDeletion(objectKeys, source);
    }

    @Scheduled(
            fixedDelayString = "${alicia.cloud-object-cleanup.retry-fixed-delay-ms:300000}",
            initialDelayString = "${alicia.cloud-object-cleanup.retry-initial-delay-ms:60000}"
    )
    public void retryDueCleanupTasks() {
        if (!cleanupEnabled) {
            return;
        }

        try {
            List<Long> dueTaskIds = cleanupTaskProcessor.findDueTaskIds(batchSize);
            if (!dueTaskIds.isEmpty()) {
                cleanupTaskProcessor.processTasks(dueTaskIds);
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to retry cloud object cleanup tasks: {}", exception.getMessage());
        }
    }

    private void trackAndScheduleDeletion(Collection<String> rawObjectKeys, CloudObjectCleanupSource source) {
        List<String> objectKeys = normalizeObjectKeys(rawObjectKeys);
        if (objectKeys.isEmpty()) {
            return;
        }
        if (source == null) {
            throw new IllegalArgumentException("Cloud object cleanup source is required.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<CloudObjectCleanupTask> tasks = objectKeys.stream()
                .map(objectKey -> CloudObjectCleanupTask.create(objectKey, source, now))
                .toList();
        List<Long> taskIds = cleanupTaskRepository.saveAllAndFlush(tasks).stream()
                .map(CloudObjectCleanupTask::getId)
                .filter(Objects::nonNull)
                .toList();

        if (taskIds.isEmpty()) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    processTasksSafely(taskIds);
                }
            });
        } else {
            processTasksSafely(taskIds);
        }
    }

    private List<String> normalizeObjectKeys(Collection<String> rawObjectKeys) {
        if (rawObjectKeys == null || rawObjectKeys.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> objectKeys = new LinkedHashSet<>();
        for (String rawObjectKey : rawObjectKeys) {
            if (rawObjectKey != null && !rawObjectKey.isBlank()) {
                objectKeys.add(rawObjectKey.trim());
            }
        }
        return List.copyOf(objectKeys);
    }

    private void processTasksSafely(List<Long> taskIds) {
        try {
            cleanupTaskProcessor.processTasks(taskIds);
        } catch (RuntimeException exception) {
            log.warn("Failed to process cloud object cleanup tasks after commit: {}", exception.getMessage());
        }
    }
}
