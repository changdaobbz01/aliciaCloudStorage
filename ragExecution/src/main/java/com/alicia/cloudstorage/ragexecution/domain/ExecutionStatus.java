package com.alicia.cloudstorage.ragexecution.domain;

public enum ExecutionStatus {
    PENDING_CONFIRMATION(false),
    QUEUED(false),
    RUNNING(false),
    RETRY_WAIT(false),
    WAITING_CLIENT_INPUT(false),
    SUCCEEDED(true),
    PARTIALLY_SUCCEEDED(true),
    FAILED(true),
    CANCELLED(true),
    EXPIRED(true);

    private final boolean terminal;

    ExecutionStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
