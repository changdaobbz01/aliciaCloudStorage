package com.alicia.cloudstorage.ragexecution.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionStateMachineTest {

    private static final Map<ExecutionStatus, Set<ExecutionStatus>> EXPECTED = expectedTransitions();

    @Test
    void allowsExactlyTheDocumentedTransitions() {
        for (ExecutionStatus current : ExecutionStatus.values()) {
            for (ExecutionStatus target : ExecutionStatus.values()) {
                assertThat(ExecutionStateMachine.canTransition(current, target))
                        .as("transition %s -> %s", current, target)
                        .isEqualTo(EXPECTED.getOrDefault(current, Set.of()).contains(target));
            }
        }
    }

    @Test
    void rejectsNullAndIllegalTransitionsWithStableException() {
        assertThat(ExecutionStateMachine.canTransition(null, ExecutionStatus.QUEUED)).isFalse();
        assertThat(ExecutionStateMachine.canTransition(ExecutionStatus.QUEUED, null)).isFalse();
        assertThatThrownBy(() -> ExecutionStateMachine.requireTransition(
                ExecutionStatus.PENDING_CONFIRMATION,
                ExecutionStatus.RUNNING
        ))
                .isInstanceOf(InvalidExecutionTransitionException.class)
                .hasMessage("Execution cannot transition from PENDING_CONFIRMATION to RUNNING.");
    }

    @Test
    void terminalStatesCannotTransition() {
        for (ExecutionStatus status : ExecutionStatus.values()) {
            if (status.isTerminal()) {
                assertThat(EXPECTED).doesNotContainKey(status);
            }
        }
    }

    private static Map<ExecutionStatus, Set<ExecutionStatus>> expectedTransitions() {
        EnumMap<ExecutionStatus, Set<ExecutionStatus>> expected = new EnumMap<>(ExecutionStatus.class);
        expected.put(ExecutionStatus.PENDING_CONFIRMATION, EnumSet.of(
                ExecutionStatus.QUEUED,
                ExecutionStatus.CANCELLED,
                ExecutionStatus.EXPIRED
        ));
        expected.put(ExecutionStatus.QUEUED, EnumSet.of(
                ExecutionStatus.RUNNING,
                ExecutionStatus.CANCELLED,
                ExecutionStatus.EXPIRED
        ));
        expected.put(ExecutionStatus.RUNNING, EnumSet.of(
                ExecutionStatus.QUEUED,
                ExecutionStatus.SUCCEEDED,
                ExecutionStatus.PARTIALLY_SUCCEEDED,
                ExecutionStatus.RETRY_WAIT,
                ExecutionStatus.WAITING_CLIENT_INPUT,
                ExecutionStatus.FAILED,
                ExecutionStatus.EXPIRED
        ));
        expected.put(ExecutionStatus.RETRY_WAIT, EnumSet.of(
                ExecutionStatus.QUEUED,
                ExecutionStatus.FAILED,
                ExecutionStatus.EXPIRED
        ));
        expected.put(ExecutionStatus.WAITING_CLIENT_INPUT, EnumSet.of(
                ExecutionStatus.QUEUED,
                ExecutionStatus.SUCCEEDED,
                ExecutionStatus.PARTIALLY_SUCCEEDED,
                ExecutionStatus.FAILED,
                ExecutionStatus.CANCELLED,
                ExecutionStatus.EXPIRED
        ));
        return Map.copyOf(expected);
    }
}
