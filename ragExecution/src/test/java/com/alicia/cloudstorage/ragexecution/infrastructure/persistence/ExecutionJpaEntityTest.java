package com.alicia.cloudstorage.ragexecution.infrastructure.persistence;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionRisk;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionStatus;
import com.alicia.cloudstorage.ragexecution.domain.InvalidExecutionTransitionException;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionJpaEntityTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-18T04:00:00Z");

    @Test
    void transitionsOnlyThroughTheDomainStateMachineAndTracksLifecycleTimes() {
        ExecutionJpaEntity execution = pendingExecution();
        Instant queuedAt = CREATED_AT.plusSeconds(30);
        Instant startedAt = queuedAt.plusSeconds(5);
        Instant finishedAt = startedAt.plusSeconds(2);

        execution.transitionTo(ExecutionStatus.QUEUED, queuedAt);
        execution.claimLease("worker-1", startedAt, startedAt.plusSeconds(30));
        execution.transitionTo(ExecutionStatus.SUCCEEDED, finishedAt);

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(execution.getConfirmedAt()).isEqualTo(queuedAt);
        assertThat(execution.getQueuedAt()).isEqualTo(queuedAt);
        assertThat(execution.getStartedAt()).isEqualTo(startedAt);
        assertThat(execution.getFinishedAt()).isEqualTo(finishedAt);
        assertThat(execution.getLeaseOwner()).isNull();
        assertThat(execution.getLeaseUntil()).isNull();
        assertThat(execution.getHeartbeatAt()).isNull();
    }

    @Test
    void cannotSkipConfirmationOrLeaseExpiredTask() {
        ExecutionJpaEntity execution = pendingExecution();

        assertThatThrownBy(() -> execution.transitionTo(ExecutionStatus.RUNNING, CREATED_AT.plusSeconds(1)))
                .isInstanceOf(InvalidExecutionTransitionException.class);

        execution.transitionTo(ExecutionStatus.QUEUED, CREATED_AT.plusSeconds(30));
        assertThatThrownBy(() -> execution.claimLease(
                "worker-1",
                CREATED_AT.plusSeconds(601),
                CREATED_AT.plusSeconds(631)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expired");
    }

    @Test
    void cannotReclaimRunningExecutionAfterItsOverallDeadline() {
        ExecutionJpaEntity execution = pendingExecution();
        Instant startedAt = CREATED_AT.plusSeconds(30);
        execution.transitionTo(ExecutionStatus.QUEUED, startedAt);
        execution.claimLease("worker-1", startedAt, startedAt.plusSeconds(30));

        assertThatThrownBy(() -> execution.reclaimLease(
                "worker-2",
                CREATED_AT.plusSeconds(601),
                CREATED_AT.plusSeconds(631)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expired execution");
    }

    @Test
    void versionFieldUsesJpaOptimisticLocking() throws NoSuchFieldException {
        assertThat(ExecutionJpaEntity.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
    }

    private ExecutionJpaEntity pendingExecution() {
        return ExecutionJpaEntity.createPending(
                UUID.randomUUID(),
                42L,
                "conversation-1",
                "response-1",
                "plan-1",
                "action_plan_v2",
                "a".repeat(64),
                "Rename the selected node",
                ExecutionRisk.MEDIUM,
                "idempotency-1",
                CREATED_AT.plusSeconds(600),
                CREATED_AT
        );
    }
}
