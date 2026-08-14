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
        if (semanticFrame == null
                || semanticFrame.clarification() == null
                || semanticFrame.clarification().reason().isBlank()
                && semanticFrame.clarification().question().isBlank()) {
            return this;
        }
        String question = semanticFrame.clarification() == null
                ? ""
                : semanticFrame.clarification().question();
        if (question.isBlank()) {
            question = clarificationQuestion == null ? "" : clarificationQuestion.trim();
        }
        question = question.isBlank()
                ? "我还不能安全确定这次操作的对象或范围，请补充一个更明确的名称或范围。"
                : question;
        List<String> suggestions = semanticFrame.clarification().suggestions();
        String suggestionLead = question.contains("例如") || question.contains("比如")
                ? " 也可以直接说："
                : " 比如：";
        String guidance = suggestions.isEmpty()
                ? question
                : question + suggestionLead + suggestions.stream()
                .map(suggestion -> "“" + suggestion + "”")
                .collect(java.util.stream.Collectors.joining("、")) + "。";
        boolean blocksExecution = AssistantFlowPolicy.isOperational(semanticFrame, actionDraft);
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
                blocksExecution ? new ActionDraft("none", Map.of(), false) : actionDraft,
                blocksExecution
                        ? BackendActionDraft.skipped("clarification_required", guidance)
                        : backendActionDraft,
                blocksExecution ? ActionPlan.skipped("clarification_required", guidance) : actionPlan,
                guidance,
                question,
                clarificationReason(semanticFrame),
                fallbackReason,
                blocksExecution
                        ? CandidateBindingResult.skipped("waiting_for_clarification", guidance)
                        : candidateBinding,
                conversation,
                semanticFrame,
                interaction
        );
    }

    private String clarificationReason(SemanticFrame frame) {
        String reason = frame.clarification() == null ? "" : frame.clarification().reason();
        if (!reason.isBlank()) {
            return reason;
        }
        return frame.ambiguities().isEmpty() ? "clarification_required" : frame.ambiguities().getFirst();
    }

    public IntentRecognitionResponse withCapabilityBoundary(
            String boundaryId,
            String userMessage,
            String guidance
    ) {
        String marker = "capability_boundary:" + (boundaryId == null ? "unknown" : boundaryId);
        String safeMessage = userMessage == null ? "请把需求描述得更明确一些。" : userMessage.trim();
        String safeGuidance = guidance == null ? "" : guidance.trim();
        return new IntentRecognitionResponse(
                id,
                schemaVersion,
                templateId,
                provider,
                model,
                message,
                "fallback",
                intentName,
                taskType,
                1.0,
                userGoal,
                normalizedQuery,
                Map.of(),
                List.of(),
                List.of(),
                "ask_clarification",
                new SafetyDecision("none", false, false, marker),
                new ActionDraft("none", Map.of(), false),
                BackendActionDraft.skipped("clarification_required", safeMessage),
                ActionPlan.skipped("clarification_required", safeMessage),
                safeMessage,
                safeGuidance,
                marker,
                marker,
                CandidateBindingResult.skipped("capability_boundary", safeMessage),
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

    public IntentRecognitionResponse withAssistantText(String text) {
        String safeText = text == null ? "" : text.trim();
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
                safeText,
                clarificationQuestion,
                reason,
                fallbackReason,
                candidateBinding,
                conversation,
                semanticFrame,
                interaction
        );
    }

    public IntentRecognitionResponse withPlanningOverride(
            String intentId,
            String intentName,
            String taskType,
            Map<String, Object> entities,
            String nextAction,
            SafetyDecision safety,
            ActionDraft actionDraft,
            String assistantText,
            String reason,
            SemanticFrame semanticFrame
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
                List.of(),
                List.of(),
                nextAction,
                safety,
                actionDraft,
                BackendActionDraft.skipped("not_confirmed", "用户尚未确认，未生成执行请求。"),
                ActionPlan.skipped("understanding", "操作计划正在生成。"),
                assistantText,
                "",
                reason,
                fallbackReason,
                CandidateBindingResult.skipped("not_requested", "候选绑定尚未执行。"),
                conversation,
                semanticFrame,
                interaction
        );
    }

    public IntentRecognitionResponse withPlanningState(
            Map<String, Object> entities,
            CandidateBindingResult candidateBinding,
            ActionPlan actionPlan,
            String nextAction,
            String assistantText
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
}
