package com.alicia.cloudstorage.rag.assistant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AssistantInteraction(
        String stage,
        List<AllowedAction> allowedActions,
        SemanticFrame.Clarification clarification
) {
    public AssistantInteraction {
        stage = stage == null || stage.isBlank() ? "IDLE" : stage.trim().toUpperCase();
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        clarification = clarification == null ? SemanticFrame.Clarification.empty() : clarification;
    }

    public static AssistantInteraction idle() {
        return new AssistantInteraction("IDLE", List.of(), SemanticFrame.Clarification.empty());
    }

    public record AllowedAction(
            String type,
            String label,
            Map<String, Object> payload
    ) {
        public AllowedAction {
            type = type == null ? "" : type.trim().toUpperCase();
            label = label == null ? "" : label.trim();
            payload = payload == null || payload.isEmpty()
                    ? Map.of()
                    : Map.copyOf(new LinkedHashMap<>(payload));
        }
    }
}
