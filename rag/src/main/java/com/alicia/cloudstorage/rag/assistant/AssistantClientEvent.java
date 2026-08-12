package com.alicia.cloudstorage.rag.assistant;

public record AssistantClientEvent(
        String type,
        Long candidateId,
        Integer candidateIndex
) {
    public AssistantClientEvent {
        type = type == null ? "" : type.trim().toUpperCase();
        candidateIndex = candidateIndex != null && candidateIndex > 0 ? candidateIndex : null;
    }

    public static AssistantClientEvent none() {
        return new AssistantClientEvent("", null, null);
    }

    public boolean isCandidateSelection() {
        return "SELECT_CANDIDATE".equals(type);
    }
}
