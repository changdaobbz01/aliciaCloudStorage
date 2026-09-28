package com.alicia.cloudstorage.rag.execution;

public interface ExecutionRegistrationClient {

    ExecutionRegistrationResponse register(
            ExecutionRegistrationRequest request,
            String authorizationHeader
    );
}
