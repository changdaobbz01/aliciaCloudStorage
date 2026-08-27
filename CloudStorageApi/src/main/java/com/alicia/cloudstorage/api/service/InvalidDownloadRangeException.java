package com.alicia.cloudstorage.api.service;

public class InvalidDownloadRangeException extends RuntimeException {

    private final long totalLength;

    public InvalidDownloadRangeException(String message, long totalLength) {
        super(message);
        this.totalLength = Math.max(0L, totalLength);
    }

    public long getTotalLength() {
        return totalLength;
    }
}
