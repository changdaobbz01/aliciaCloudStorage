package com.alicia.cloudstorage.api.ragexecution.application;

public class CloudActionException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public CloudActionException(int statusCode, String errorCode) {
        super(errorCode);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public CloudActionException(int statusCode, String errorCode, Throwable cause) {
        super(errorCode, cause);
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
