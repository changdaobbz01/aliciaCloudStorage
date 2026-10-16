package com.alicia.cloudstorage.rag.execution;

public class ExecutionRegistrationUncertainException extends RuntimeException {

    public ExecutionRegistrationUncertainException(Throwable cause) {
        super("Execution registration outcome is uncertain.", cause);
    }
}
