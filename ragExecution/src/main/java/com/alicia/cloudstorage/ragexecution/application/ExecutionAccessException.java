package com.alicia.cloudstorage.ragexecution.application;

public class ExecutionAccessException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public ExecutionAccessException(int statusCode, String errorCode) {
        super(errorCode);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
