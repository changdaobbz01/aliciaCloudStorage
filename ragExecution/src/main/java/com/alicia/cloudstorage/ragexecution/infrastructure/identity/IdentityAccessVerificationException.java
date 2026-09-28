package com.alicia.cloudstorage.ragexecution.infrastructure.identity;

public class IdentityAccessVerificationException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public IdentityAccessVerificationException(int statusCode, String errorCode) {
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
