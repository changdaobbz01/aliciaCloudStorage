package com.alicia.cloudstorage.api.service;

import com.alicia.cloudstorage.api.entity.CloudObjectCleanupSource;
import com.alicia.cloudstorage.api.entity.CloudObjectCleanupTask;
import com.alicia.cloudstorage.api.repository.CloudObjectCleanupTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudObjectCleanupServiceTest {

    @Mock
    private CloudObjectCleanupTaskRepository cleanupTaskRepository;

    @Mock
    private CloudObjectCleanupTaskProcessor cleanupTaskProcessor;

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-08-26T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void trackAndDeleteNowDeduplicatesObjectKeysAndProcessesImmediatelyWithoutTransactionSynchronization() {
        CloudObjectCleanupService cleanupService = cleanupService(true, 10);
        when(cleanupTaskRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> assignIds(invocation.getArgument(0)));

        cleanupService.trackAndDeleteNow(
                List.of(" cos/photo.png ", "cos/photo.png", "", "cos/readme.txt"),
                CloudObjectCleanupSource.UPLOAD_METADATA_ROLLBACK
        );

        ArgumentCaptor<List<CloudObjectCleanupTask>> tasksCaptor = ArgumentCaptor.forClass(List.class);
        verify(cleanupTaskRepository).saveAllAndFlush(tasksCaptor.capture());
        assertThat(tasksCaptor.getValue())
                .extracting(CloudObjectCleanupTask::getObjectKey)
                .containsExactly("cos/photo.png", "cos/readme.txt");
        assertThat(tasksCaptor.getValue())
                .extracting(CloudObjectCleanupTask::getSource)
                .containsOnly(CloudObjectCleanupSource.UPLOAD_METADATA_ROLLBACK);
        verify(cleanupTaskProcessor).processTasks(List.of(1L, 2L));
    }

    @Test
    void trackAndDeleteAfterCommitDefersProcessingUntilTransactionCommit() {
        CloudObjectCleanupService cleanupService = cleanupService(true, 10);
        when(cleanupTaskRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> assignIds(invocation.getArgument(0)));
        TransactionSynchronizationManager.initSynchronization();

        cleanupService.trackAndDeleteAfterCommit(
                List.of("cos/deleted.txt"),
                CloudObjectCleanupSource.PERMANENT_DELETE
        );

        verify(cleanupTaskProcessor, never()).processTasks(anyList());
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        synchronizations.get(0).afterCommit();

        verify(cleanupTaskProcessor).processTasks(List.of(1L));
    }

    @Test
    void retryDueCleanupTasksProcessesDueBatch() {
        CloudObjectCleanupService cleanupService = cleanupService(true, 5);
        when(cleanupTaskProcessor.findDueTaskIds(5)).thenReturn(List.of(21L, 22L));

        cleanupService.retryDueCleanupTasks();

        verify(cleanupTaskProcessor).processTasks(List.of(21L, 22L));
    }

    @Test
    void retryDueCleanupTasksSkipsWhenCleanupIsDisabled() {
        CloudObjectCleanupService cleanupService = cleanupService(false, 5);

        cleanupService.retryDueCleanupTasks();

        verifyNoInteractions(cleanupTaskProcessor);
    }

    private CloudObjectCleanupService cleanupService(boolean cleanupEnabled, int batchSize) {
        return new CloudObjectCleanupService(
                cleanupTaskRepository,
                cleanupTaskProcessor,
                clock,
                cleanupEnabled,
                batchSize
        );
    }

    private List<CloudObjectCleanupTask> assignIds(List<CloudObjectCleanupTask> tasks) {
        AtomicLong nextId = new AtomicLong(1L);
        tasks.forEach(task -> ReflectionTestUtils.setField(task, "id", nextId.getAndIncrement()));
        return tasks;
    }
}
