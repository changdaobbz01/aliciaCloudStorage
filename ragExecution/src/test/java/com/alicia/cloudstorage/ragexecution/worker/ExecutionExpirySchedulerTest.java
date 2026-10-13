package com.alicia.cloudstorage.ragexecution.worker;

import com.alicia.cloudstorage.ragexecution.config.RagExecutionFeatureProperties;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionExpirySchedulerTest {

    @Test
    void expiresTasksWithoutDependingOnTheExecutionWorker() {
        ExecutionWorkStateService stateService = mock(ExecutionWorkStateService.class);
        when(stateService.expireOne()).thenReturn(true, true, false);
        ExecutionExpiryScheduler scheduler = scheduler(true, stateService);

        scheduler.poll();

        verify(stateService, times(3)).expireOne();
    }

    @Test
    void boundsEachMaintenancePoll() {
        ExecutionWorkStateService stateService = mock(ExecutionWorkStateService.class);
        when(stateService.expireOne()).thenReturn(true);
        ExecutionExpiryScheduler scheduler = scheduler(true, stateService);

        scheduler.poll();

        verify(stateService, times(ExecutionExpiryScheduler.MAX_EXPIRATIONS_PER_POLL)).expireOne();
    }

    @Test
    void containsMaintenanceFailuresWithoutAffectingTheScheduler() {
        ExecutionWorkStateService stateService = mock(ExecutionWorkStateService.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(stateService).expireOne();
        ExecutionExpiryScheduler scheduler = scheduler(true, stateService);

        assertThatCode(scheduler::poll).doesNotThrowAnyException();

        verify(stateService).expireOne();
    }

    @Test
    void doesNotMutateTasksWhenTheMasterFeatureIsDisabled() {
        ExecutionWorkStateService stateService = mock(ExecutionWorkStateService.class);
        ExecutionExpiryScheduler scheduler = scheduler(false, stateService);

        scheduler.poll();

        verify(stateService, times(0)).expireOne();
    }

    private ExecutionExpiryScheduler scheduler(
            boolean enabled,
            ExecutionWorkStateService stateService
    ) {
        return new ExecutionExpiryScheduler(
                new RagExecutionFeatureProperties(
                        enabled,
                        enabled,
                        false,
                        false,
                        false,
                        true,
                        Set.of()
                ),
                stateService
        );
    }
}
