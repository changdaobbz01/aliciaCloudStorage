package com.alicia.cloudstorage.rag.assistant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ConversationContextResolution(
        String relation,
        String referent,
        String contextAction,
        String rewrittenMessage,
        Map<String, Object> carriedEntities,
        List<String> requiredClientFields,
        String answerText,
        String clarificationQuestion,
        Integer selectedIndex,
        double confidence,
        String reason
) {
    public ConversationContextResolution {
        relation = clean(relation, "new_task");
        referent = clean(referent, "none");
        contextAction = clean(contextAction, "");
        rewrittenMessage = clean(rewrittenMessage, "");
        carriedEntities = carriedEntities == null || carriedEntities.isEmpty()
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(carriedEntities));
        requiredClientFields = requiredClientFields == null ? List.of() : List.copyOf(requiredClientFields);
        answerText = clean(answerText, "");
        clarificationQuestion = clean(clarificationQuestion, "");
        selectedIndex = selectedIndex == null || selectedIndex <= 0 ? null : selectedIndex;
        confidence = Math.max(0.0, Math.min(confidence, 1.0));
        reason = clean(reason, "");
    }

    public ConversationContextResolution(
            String relation,
            String referent,
            String contextAction,
            String rewrittenMessage,
            Map<String, Object> carriedEntities,
            List<String> requiredClientFields,
            String answerText,
            String clarificationQuestion,
            double confidence,
            String reason
    ) {
        this(
                relation,
                referent,
                contextAction,
                rewrittenMessage,
                carriedEntities,
                requiredClientFields,
                answerText,
                clarificationQuestion,
                null,
                confidence,
                reason
        );
    }

    public static ConversationContextResolution newTask(String reason) {
        return new ConversationContextResolution(
                "new_task",
                "none",
                "",
                "",
                Map.of(),
                List.of(),
                "",
                "",
                null,
                0.0,
                reason
        );
    }

    public boolean continuesContext() {
        return List.of(
                "follow_up_question",
                "modify_previous_action",
                "continue_previous_action",
                "slot_fill",
                "candidate_reference",
                "client_input_reference"
        ).contains(relation);
    }

    public boolean shouldAnswerDirectly() {
        return "follow_up_question".equals(relation) && !answerText.isBlank();
    }

    public boolean shouldRewrite() {
        return !rewrittenMessage.isBlank();
    }

    public boolean needsClarification() {
        return "clarification_required".equals(relation) || !clarificationQuestion.isBlank();
    }

    private static String clean(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isBlank() ? fallback : trimmed;
    }
}
