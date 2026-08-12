package com.alicia.cloudstorage.rag.assistant;

import java.util.List;
import java.util.Map;

public record IntentRecognitionResponse(
        String id,
        String schemaVersion,
        String templateId,
        String provider,
        String model,
        String message,
        String intentId,
        String intentName,
        String taskType,
        double confidence,
        String userGoal,
        String normalizedQuery,
        Map<String, Object> entities,
        List<String> requiredSlots,
        List<String> missingSlots,
        String nextAction,
        SafetyDecision safety,
        ActionDraft actionDraft,
        BackendActionDraft backendActionDraft,
        ActionPlan actionPlan,
        String assistantText,
        String clarificationQuestion,
        String reason,
        String fallbackReason,
        CandidateBindingResult candidateBinding,
        AssistantConversationSnapshot conversation,
        SemanticFrame semanticFrame,
        AssistantInteraction interaction
) {
    public IntentRecognitionResponse {
        semanticFrame = semanticFrame == null ? SemanticFrame.empty() : semanticFrame;
        interaction = interaction == null ? AssistantInteraction.idle() : interaction;
    }

    public IntentRecognitionResponse(
            String id,
            String schemaVersion,
            String templateId,
            String provider,
            String model,
            String message,
            String intentId,
            String intentName,
            String taskType,
            double confidence,
            String userGoal,
            String normalizedQuery,
            Map<String, Object> entities,
            List<String> requiredSlots,
            List<String> missingSlots,
            String nextAction,
            SafetyDecision safety,
            ActionDraft actionDraft,
            BackendActionDraft backendActionDraft,
            ActionPlan actionPlan,
            String assistantText,
            String clarificationQuestion,
            String reason,
            String fallbackReason,
            CandidateBindingResult candidateBinding,
            AssistantConversationSnapshot conversation
    ) {
        this(
                id,
                schemaVersion,
                templateId,
                provider,
                model,
                message,
                intentId,
                intentName,
                taskType,
                confidence,
                userGoal,
                normalizedQuery,
                entities,
                requiredSlots,
                missingSlots,
                nextAction,
                safety,
                actionDraft,
                backendActionDraft,
                actionPlan,
                assistantText,
                clarificationQuestion,
                reason,
                fallbackReason,
                candidateBinding,
                conversation,
                SemanticFrame.empty(),
                AssistantInteraction.idle()
        );
    }

    public IntentRecognitionResponse withConversation(AssistantConversationSnapshot conversation) {
        return new IntentRecognitionResponse(
                id,
                schemaVersion,
                templateId,
                provider,
                model,
                message,
                intentId,
                intentName,
                taskType,
                confidence,
                userGoal,
                normalizedQuery,
                entities,
                requiredSlots,
                missingSlots,
                nextAction,
                safety,
                actionDraft,
                backendActionDraft,
                actionPlan,
                assistantText,
                clarificationQuestion,
                reason,
                fallbackReason,
                candidateBinding,
                conversation,
                semanticFrame,
                interaction
        );
    }

    public IntentRecognitionResponse withCandidateBinding(CandidateBindingResult candidateBinding) {
        return new IntentRecognitionResponse(
                id,
                schemaVersion,
                templateId,
                provider,
                model,
                message,
                intentId,
                intentName,
                taskType,
                confidence,
                userGoal,
                normalizedQuery,
                entities,
                requiredSlots,
                missingSlots,
                nextAction,
                safety,
                actionDraft,
                backendActionDraft,
                actionPlan,
                assistantText,
                clarificationQuestion,
                reason,
                fallbackReason,
                candidateBinding,
                conversation,
                semanticFrame,
                interaction
        );
    }

    public IntentRecognitionResponse withBackendActionDraft(BackendActionDraft backendActionDraft) {
        return new IntentRecognitionResponse(
                id,
                schemaVersion,
                templateId,
                provider,
                model,
                message,
                intentId,
                intentName,
                taskType,
                confidence,
                userGoal,
                normalizedQuery,
                entities,
                requiredSlots,
                missingSlots,
                nextAction,
                safety,
                actionDraft,
                backendActionDraft,
                actionPlan,
                assistantText,
                clarificationQuestion,
                reason,
                fallbackReason,
                candidateBinding,
                conversation,
                semanticFrame,
                interaction
        );
    }

    public IntentRecognitionResponse withActionPlan(ActionPlan actionPlan) {
        return new IntentRecognitionResponse(
                id,
                schemaVersion,
                templateId,
                provider,
                model,
                message,
                intentId,
                intentName,
                taskType,
                confidence,
                userGoal,
                normalizedQuery,
                entities,
                requiredSlots,
                missingSlots,
                nextAction,
                safety,
                actionDraft,
                backendActionDraft,
                actionPlan,
                assistantText,
                clarificationQuestion,
                reason,
                fallbackReason,
                candidateBinding,
                conversation,
                semanticFrame,
                interaction
        );
    }

    public IntentRecognitionResponse withSemanticQueryPlan(
            Map<String, Object> entities,
            ActionDraft actionDraft
    ) {
        return new IntentRecognitionResponse(
                id,
                schemaVersion,
                templateId,
                provider,
                model,
                message,
                intentId,
                intentName,
                taskType,
                confidence,
                userGoal,
                normalizedQuery,
                entities,
                requiredSlots,
                missingSlots,
                nextAction,
                safety,
                actionDraft,
                backendActionDraft,
                actionPlan,
                assistantText,
                clarificationQuestion,
                reason,
                fallbackReason,
                candidateBinding,
                conversation,
                semanticFrame,
                interaction
        );
    }

    public IntentRecognitionResponse withSemanticFrame(
            SemanticFrame semanticFrame,
            Map<String, Object> entities,
            ActionDraft actionDraft
    ) {
        return new IntentRecognitionResponse(
                id,
                schemaVersion,
                templateId,
                provider,
                model,
                message,
                intentId,
                intentName,
                taskType,
                semanticFrame == null ? confidence : semanticFrame.confidence(),
                userGoal,
                normalizedQuery,
                entities,
                requiredSlots,
                missingSlots,
                nextAction,
                safety,
                actionDraft,
                backendActionDraft,
                actionPlan,
                assistantText,
                clarificationQuestion,
                reason,
                fallbackReason,
                candidateBinding,
                conversation,
                semanticFrame,
                interaction
        );
    }

    public IntentRecognitionResponse withSemanticClarification(SemanticFrame semanticFrame) {
        String question = semanticFrame == null || semanticFrame.clarification() == null
                ? ""
                : semanticFrame.clarification().question();
        if (question.isBlank()) {
            return this;
        }
        List<String> suggestions = semanticFrame.clarification().suggestions();
        String suggestionLead = question.contains("例如") || question.contains("比如")
                ? " 也可以直接说："
                : " 比如：";
        String guidance = suggestions.isEmpty()
                ? question
                : question + suggestionLead + suggestions.stream()
                .map(suggestion -> "“" + suggestion + "”")
                .collect(java.util.stream.Collectors.joining("、")) + "。";
        return new IntentRecognitionResponse(
                id,
                schemaVersion,
                templateId,
                provider,
                model,
                message,
                intentId,
                intentName,
                taskType,
                semanticFrame.confidence(),
                userGoal,
                normalizedQuery,
                entities,
                requiredSlots,
                missingSlots,
                "ask_clarification",
                safety,
                actionDraft,
                backendActionDraft,
                actionPlan,
                guidance,
                question,
                semanticFrame.clarification().reason(),
                fallbackReason,
                candidateBinding,
                conversation,
                semanticFrame,
                interaction
        );
    }

    public IntentRecognitionResponse withInteraction(AssistantInteraction interaction) {
        return new IntentRecognitionResponse(
                id,
                schemaVersion,
                templateId,
                provider,
                model,
                message,
                intentId,
                intentName,
                taskType,
                confidence,
                userGoal,
                normalizedQuery,
                entities,
                requiredSlots,
                missingSlots,
                nextAction,
                safety,
                actionDraft,
                backendActionDraft,
                actionPlan,
                assistantText,
                clarificationQuestion,
                reason,
                fallbackReason,
                candidateBinding,
                conversation,
                semanticFrame,
                interaction
        );
    }
}
