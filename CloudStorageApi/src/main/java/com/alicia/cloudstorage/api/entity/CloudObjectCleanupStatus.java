package com.alicia.cloudstorage.api.entity;

public enum CloudObjectCleanupStatus {
    PENDING,
    RETRYING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
