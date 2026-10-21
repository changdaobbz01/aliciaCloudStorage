package com.alicia.cloudstorage.rag.assistant;

final class PublicAssistantResponseSanitizer {

    private PublicAssistantResponseSanitizer() {
    }

    static IntentRecognitionResponse sanitize(IntentRecognitionResponse response) {
        if (response == null || response.backendActionDraft() == null) {
            return response;
        }
        return response.withBackendActionDraft(response.backendActionDraft().forPublicClient());
    }
}
