package com.alicia.cloudstorage.ragexecution.infrastructure.cloud;

public class CloudActionDispatchException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public CloudActionDispatchException(String errorCode, boolean retryable) {
        super(errorCode);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public CloudActionDispatchException(String errorCode, boolean retryable, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
