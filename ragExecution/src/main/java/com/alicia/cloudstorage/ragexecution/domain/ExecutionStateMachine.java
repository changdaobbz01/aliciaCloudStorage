package com.alicia.cloudstorage.ragexecution.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class ExecutionStateMachine {

    private static final Map<ExecutionStatus, Set<ExecutionStatus>> ALLOWED_TRANSITIONS = allowedTransitions();

    private ExecutionStateMachine() {
    }

    public static boolean canTransition(ExecutionStatus current, ExecutionStatus target) {
        if (current == null || target == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
    }

    public static void requireTransition(ExecutionStatus current, ExecutionStatus target) {
        if (!canTransition(current, target)) {
            throw new InvalidExecutionTransitionException(current, target);
        }
    }

    private static Map<ExecutionStatus, Set<ExecutionStatus>> allowedTransitions() {
        EnumMap<ExecutionStatus, Set<ExecutionStatus>> transitions = new EnumMap<>(ExecutionStatus.class);
        transitions.put(ExecutionStatus.PENDING_CONFIRMATION, EnumSet.of(
                ExecutionStatus.QUEUED,
                ExecutionStatus.CANCELLED,
                ExecutionStatus.EXPIRED
        ));
        transitions.put(ExecutionStatus.QUEUED, EnumSet.of(
                ExecutionStatus.RUNNING,
                ExecutionStatus.CANCELLED,
                ExecutionStatus.EXPIRED
        ));
        transitions.put(ExecutionStatus.RUNNING, EnumSet.of(
                ExecutionStatus.QUEUED,
                ExecutionStatus.SUCCEEDED,
                ExecutionStatus.PARTIALLY_SUCCEEDED,
                ExecutionStatus.RETRY_WAIT,
                ExecutionStatus.WAITING_CLIENT_INPUT,
                ExecutionStatus.FAILED,
                ExecutionStatus.EXPIRED
        ));
        transitions.put(ExecutionStatus.RETRY_WAIT, EnumSet.of(
                ExecutionStatus.QUEUED,
                ExecutionStatus.FAILED,
                ExecutionStatus.EXPIRED
        ));
        transitions.put(ExecutionStatus.WAITING_CLIENT_INPUT, EnumSet.of(
                ExecutionStatus.QUEUED,
                ExecutionStatus.SUCCEEDED,
                ExecutionStatus.PARTIALLY_SUCCEEDED,
                ExecutionStatus.FAILED,
                ExecutionStatus.CANCELLED,
                ExecutionStatus.EXPIRED
        ));
        return Map.copyOf(transitions);
    }
}
