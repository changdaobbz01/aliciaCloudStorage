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
        AssistantConversationSnapshot conversation
) {
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
                conversation
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
                conversation
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
                conversation
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
                conversation
        );
    }
}
