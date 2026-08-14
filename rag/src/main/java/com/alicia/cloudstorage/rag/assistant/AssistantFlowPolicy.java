package com.alicia.cloudstorage.rag.assistant;

/** Centralizes flow gates shared by conversation, planning, and execution layers. */
final class AssistantFlowPolicy {

    private AssistantFlowPolicy() {
    }

    static boolean requiresClarification(IntentRecognitionResponse response) {
        if (response == null) {
            return true;
        }
        return hasBlockingSemanticClarification(response)
                || response.missingSlots() != null && !response.missingSlots().isEmpty()
                || "ask_clarification".equals(response.nextAction())
                || isCapabilityBoundary(response);
    }

    static boolean blocksExecution(IntentRecognitionResponse response) {
        if (response == null || !requiresClarification(response)) {
            return false;
        }
        return isOperational(response.semanticFrame(), response.actionDraft());
    }

    static boolean isOperational(SemanticFrame frame, ActionDraft actionDraft) {
        String actionType = actionDraft == null ? "none" : actionDraft.type();
        boolean hasAction = actionType != null && !actionType.isBlank() && !"none".equals(actionType);
        boolean hasOperationalFrame = frame != null
                && !"UNKNOWN".equals(frame.operation())
                && !"RESPOND".equals(frame.operation());
        return hasAction || hasOperationalFrame;
    }

    static boolean hasBlockingSemanticClarification(IntentRecognitionResponse response) {
        if (response == null || response.semanticFrame() == null) {
            return false;
        }
        SemanticFrame.Clarification clarification = response.semanticFrame().clarification();
        return clarification != null
                && (!clarification.reason().isBlank() || !clarification.question().isBlank());
    }

    static boolean isCapabilityBoundary(IntentRecognitionResponse response) {
        return response != null
                && response.fallbackReason() != null
                && response.fallbackReason().startsWith("capability_boundary:");
    }
}
