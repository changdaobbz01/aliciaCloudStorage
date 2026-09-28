package com.alicia.cloudstorage.api.ragexecution.security;

public class CloudActionAuthenticationException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public CloudActionAuthenticationException(int statusCode, String errorCode) {
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
