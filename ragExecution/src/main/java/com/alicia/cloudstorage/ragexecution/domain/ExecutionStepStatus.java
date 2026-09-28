package com.alicia.cloudstorage.ragexecution.domain;

public enum ExecutionStepStatus {
    PENDING,
    RUNNING,
    RETRY_WAIT,
    WAITING_CLIENT_INPUT,
    SUCCEEDED,
    FAILED,
    SKIPPED
}
