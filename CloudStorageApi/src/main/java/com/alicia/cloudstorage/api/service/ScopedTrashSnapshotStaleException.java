package com.alicia.cloudstorage.api.service;

public class ScopedTrashSnapshotStaleException extends RuntimeException {

    public ScopedTrashSnapshotStaleException(String message) {
        super(message);
    }
}
