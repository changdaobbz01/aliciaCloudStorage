package com.alicia.cloudstorage.ragexecution.domain;

public class InvalidExecutionTransitionException extends IllegalStateException {

    public InvalidExecutionTransitionException(ExecutionStatus current, ExecutionStatus target) {
        super("Execution cannot transition from " + current + " to " + target + ".");
    }
}
