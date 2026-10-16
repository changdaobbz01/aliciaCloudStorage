package com.alicia.cloudstorage.ragexecution.api.internal;

public record RecoverExecutionRequest(
        String planId,
        String planHash
) {
}
