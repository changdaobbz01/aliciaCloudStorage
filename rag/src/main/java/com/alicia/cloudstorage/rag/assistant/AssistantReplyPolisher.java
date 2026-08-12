package com.alicia.cloudstorage.rag.assistant;

import java.util.List;
import java.util.Optional;

@FunctionalInterface
public interface AssistantReplyPolisher {

    Optional<String> polish(PolishRequest request);

    static AssistantReplyPolisher noop() {
        return request -> Optional.empty();
    }

    record PolishRequest(
            String userMessage,
            String intentId,
            String intentName,
            String taskType,
            String nextAction,
            String actionType,
            String risk,
            boolean requiresConfirmation,
            List<String> missingSlots,
            String templateText
    ) {
        public PolishRequest {
            userMessage = userMessage == null ? "" : userMessage;
            intentId = intentId == null ? "" : intentId;
            intentName = intentName == null ? "" : intentName;
            taskType = taskType == null ? "" : taskType;
            nextAction = nextAction == null ? "" : nextAction;
            actionType = actionType == null ? "none" : actionType;
            risk = risk == null ? "none" : risk;
            missingSlots = missingSlots == null ? List.of() : List.copyOf(missingSlots);
            templateText = templateText == null ? "" : templateText;
        }
    }
}
