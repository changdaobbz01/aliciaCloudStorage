package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.CloudObjectCleanupSource;
import com.alicia.cloudstorage.api.entity.CloudObjectCleanupStatus;
import com.alicia.cloudstorage.api.entity.CloudObjectCleanupTask;
import com.alicia.cloudstorage.api.repository.CloudObjectCleanupTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudObjectCleanupTaskProcessorTest {

    @Mock
    private CloudObjectCleanupTaskRepository cleanupTaskRepository;

    @Mock
    private CosFileStorageService cosFileStorageService;

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-08-26T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    }

    @Test
    void processTasksDeletesObjectAndMarksTaskCompleted() {
        CloudObjectCleanupTask task = cleanupTask(10L, "cos/photo.png");
        CloudObjectCleanupTaskProcessor processor = processor(3, 60L, 600L);
        when(cleanupTaskRepository.findById(10L)).thenReturn(Optional.of(task));

        processor.processTasks(List.of(10L, 10L));

        verify(cosFileStorageService, times(1)).deleteObject("cos/photo.png");
        assertThat(task.getStatus()).isEqualTo(CloudObjectCleanupStatus.COMPLETED);
        assertThat(task.getCompletedAt()).isEqualTo(LocalDateTime.now(clock));
        assertThat(task.getLastError()).isNull();
    }

    @Test
    void processTasksKeepsTaskRetryableWhenCosDeleteFailsBeforeMaxAttempts() {
        CloudObjectCleanupTask task = cleanupTask(11L, "cos/retry.png");
        CloudObjectCleanupTaskProcessor processor = processor(3, 60L, 600L);
        when(cleanupTaskRepository.findById(11L)).thenReturn(Optional.of(task));
        doThrow(new CosStorageException("COS timeout", null)).when(cosFileStorageService).deleteObject("cos/retry.png");

        processor.processTasks(List.of(11L));

        assertThat(task.getStatus()).isEqualTo(CloudObjectCleanupStatus.RETRYING);
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getNextRetryAt()).isEqualTo(LocalDateTime.now(clock).plusSeconds(60L));
        assertThat(task.getLastError()).isEqualTo("CosStorageException: cos timeout");
    }

    @Test
    void processTasksMarksTaskFailedAtMaxAttempts() {
        CloudObjectCleanupTask task = cleanupTask(12L, "cos/fail.png");
        CloudObjectCleanupTaskProcessor processor = processor(1, 60L, 600L);
        when(cleanupTaskRepository.findById(12L)).thenReturn(Optional.of(task));
        doThrow(new CosStorageException("COS permission denied", null)).when(cosFileStorageService).deleteObject("cos/fail.png");

        processor.processTasks(List.of(12L));

        assertThat(task.getStatus()).isEqualTo(CloudObjectCleanupStatus.FAILED);
        assertThat(task.getAttempts()).isEqualTo(1);
    }

    private CloudObjectCleanupTask cleanupTask(Long id, String objectKey) {
        CloudObjectCleanupTask task = CloudObjectCleanupTask.create(
                objectKey,
                CloudObjectCleanupSource.PERMANENT_DELETE,
                LocalDateTime.now(clock).minusMinutes(1)
        );
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    private CloudObjectCleanupTaskProcessor processor(int maxAttempts, long retryBaseDelaySeconds, long retryMaxDelaySeconds) {
        return new CloudObjectCleanupTaskProcessor(
                cleanupTaskRepository,
                cosFileStorageService,
                clock,
                maxAttempts,
                retryBaseDelaySeconds,
                retryMaxDelaySeconds
        );
    }
}
