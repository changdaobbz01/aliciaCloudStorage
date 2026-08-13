package com.alicia.cloudstorage.rag.assistant;

public record AssistantStreamEvent(
        String type,
        String text,
        IntentRecognitionResponse response
) {
    public static AssistantStreamEvent status(String text) {
        return new AssistantStreamEvent("status", text, null);
    }

    public static AssistantStreamEvent delta(String text) {
        return new AssistantStreamEvent("assistant_text_delta", text, null);
    }

    public static AssistantStreamEvent finalResponse(IntentRecognitionResponse response) {
        return new AssistantStreamEvent("final", "", response);
    }

    public static AssistantStreamEvent error(String text) {
        return new AssistantStreamEvent("error", text, null);
    }

    public static AssistantStreamEvent done() {
        return new AssistantStreamEvent("done", "", null);
    }
}
