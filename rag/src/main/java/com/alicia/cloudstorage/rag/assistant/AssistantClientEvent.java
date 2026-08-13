package com.alicia.cloudstorage.rag.assistant;

import java.util.List;

public record AssistantClientEvent(
        String type,
        Long candidateId,
        Integer candidateIndex,
        String bindingKey,
        String planId,
        String outcome
) {
    public AssistantClientEvent {
        type = type == null ? "" : type.trim().toUpperCase();
        candidateIndex = candidateIndex != null && candidateIndex > 0 ? candidateIndex : null;
        bindingKey = bindingKey == null ? "" : bindingKey.trim();
        planId = planId == null ? "" : planId.trim();
        outcome = outcome == null ? "" : outcome.trim();
    }

    public AssistantClientEvent(String type, Long candidateId, Integer candidateIndex) {
        this(type, candidateId, candidateIndex, "", "", "");
    }

    public static AssistantClientEvent none() {
        return new AssistantClientEvent("", null, null, "", "", "");
    }

    public boolean isCandidateSelection() {
        return "SELECT_CANDIDATE".equals(type);
    }

    public boolean isExecutionTerminal() {
        return List.of("ACTION_COMPLETED", "ACTION_FAILED", "ACTION_CANCELLED").contains(type);
    }
}
