package com.alicia.cloudstorage.rag.execution;

import com.alicia.cloudstorage.rag.assistant.IntentRecognitionResponse;

@FunctionalInterface
public interface ShadowExecutionRegistrar {

    IntentRecognitionResponse registerIfEligible(
            IntentRecognitionResponse response,
            String authorizationHeader
    );

    static ShadowExecutionRegistrar disabled() {
        return (response, authorizationHeader) -> response;
    }
}
