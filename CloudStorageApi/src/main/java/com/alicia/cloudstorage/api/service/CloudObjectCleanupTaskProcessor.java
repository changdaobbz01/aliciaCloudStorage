package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.CloudObjectCleanupStatus;
import com.alicia.cloudstorage.api.entity.CloudObjectCleanupTask;
import com.alicia.cloudstorage.api.repository.CloudObjectCleanupTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class CloudObjectCleanupTaskProcessor {

    private static final Logger log = LoggerFactory.getLogger(CloudObjectCleanupTaskProcessor.class);
    private static final int MAX_EXPONENTIAL_DELAY_POWER = 6;

    private final CloudObjectCleanupTaskRepository cleanupTaskRepository;
    private final CosFileStorageService cosFileStorageService;
    private final Clock clock;
    private final int maxAttempts;
    private final long retryBaseDelaySeconds;
    private final long retryMaxDelaySeconds;

    public CloudObjectCleanupTaskProcessor(
            CloudObjectCleanupTaskRepository cleanupTaskRepository,
            CosFileStorageService cosFileStorageService,
            Clock clock,
            @Value("${alicia.cloud-object-cleanup.max-attempts:8}") int maxAttempts,
            @Value("${alicia.cloud-object-cleanup.retry-base-delay-seconds:300}") long retryBaseDelaySeconds,
            @Value("${alicia.cloud-object-cleanup.retry-max-delay-seconds:86400}") long retryMaxDelaySeconds
    ) {
        this.cleanupTaskRepository = cleanupTaskRepository;
        this.cosFileStorageService = cosFileStorageService;
        this.clock = clock;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBaseDelaySeconds = Math.max(1L, retryBaseDelaySeconds);
        this.retryMaxDelaySeconds = Math.max(this.retryBaseDelaySeconds, retryMaxDelaySeconds);
    }

    @Transactional(readOnly = true)
    public List<Long> findDueTaskIds(int batchSize) {
        return cleanupTaskRepository.findDueTaskIds(
                List.of(CloudObjectCleanupStatus.PENDING, CloudObjectCleanupStatus.RETRYING),
                LocalDateTime.now(clock),
                PageRequest.of(0, Math.max(1, batchSize))
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processTasks(Collection<Long> rawTaskIds) {
        if (rawTaskIds == null || rawTaskIds.isEmpty()) {
            return;
        }

        for (Long taskId : new LinkedHashSet<>(rawTaskIds)) {
            processTask(taskId);
        }
    }

    private void processTask(Long taskId) {
        if (taskId == null) {
            return;
        }

        CloudObjectCleanupTask task = cleanupTaskRepository.findById(taskId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!shouldProcess(task, now)) {
            return;
        }

        try {
            cosFileStorageService.deleteObject(task.getObjectKey());
            task.markCompleted(now);
            log.info(
                    "Completed cloud object cleanup task id={}, source={}, objectKey={}",
                    task.getId(),
                    task.getSource(),
                    task.getObjectKey()
            );
        } catch (RuntimeException exception) {
            LocalDateTime nextRetryAt = now.plusSeconds(calculateRetryDelaySeconds(task.getAttempts() + 1));
            task.markFailed(summarizeException(exception), nextRetryAt, maxAttempts);
            log.warn(
                    "Cloud object cleanup task id={} failed with status={}, attempts={}, source={}, nextRetryAt={}: {}",
                    task.getId(),
                    task.getStatus(),
                    task.getAttempts(),
                    task.getSource(),
                    task.getNextRetryAt(),
                    summarizeException(exception)
            );
        }
    }

    private boolean shouldProcess(CloudObjectCleanupTask task, LocalDateTime now) {
        return task != null
                && !task.getStatus().isTerminal()
                && !task.getNextRetryAt().isAfter(now);
    }

    private long calculateRetryDelaySeconds(int nextAttempt) {
        int exponent = Math.min(Math.max(0, nextAttempt - 1), MAX_EXPONENTIAL_DELAY_POWER);
        long delay = retryBaseDelaySeconds * (1L << exponent);
        return Math.min(delay, retryMaxDelaySeconds);
    }

    private String summarizeException(RuntimeException exception) {
        String type = exception.getClass().getSimpleName();
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return type;
        }
        return type + ": " + message.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
