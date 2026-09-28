package com.alicia.cloudstorage.ragexecution.infrastructure.security;

public class ServiceRequestAuthenticationException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public ServiceRequestAuthenticationException(int statusCode, String errorCode) {
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
