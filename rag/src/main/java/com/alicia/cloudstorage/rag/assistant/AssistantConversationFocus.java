package com.alicia.cloudstorage.rag.assistant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AssistantConversationFocus(
        String focusKind,
        String sourceIntentId,
        String actionType,
        Map<String, Object> entities,
        CandidateBindingResult candidateBinding,
        CandidateItem selectedCandidate
) {
    public AssistantConversationFocus {
        focusKind = focusKind == null || focusKind.isBlank() ? "none" : focusKind;
        sourceIntentId = sourceIntentId == null ? "" : sourceIntentId;
        actionType = actionType == null ? "" : actionType;
        entities = entities == null || entities.isEmpty() ? Map.of() : Map.copyOf(new LinkedHashMap<>(entities));
    }

    public static AssistantConversationFocus empty() {
        return new AssistantConversationFocus("none", "", "", Map.of(), null, null);
    }

    public static AssistantConversationFocus next(
            AssistantConversationFocus previous,
            IntentRecognitionResponse response
    ) {
        if (response == null) {
            return previous == null ? empty() : previous;
        }

        CandidateBindingResult binding = response.candidateBinding();
        if (binding != null && !binding.candidates().isEmpty()) {
            CandidateItem selected = selectedCandidate(binding);
            return new AssistantConversationFocus(
                    selected == null ? "candidate_set" : "candidate",
                    response.intentId(),
                    response.actionDraft() == null ? "none" : response.actionDraft().type(),
                    response.entities(),
                    binding,
                    selected
            );
        }

        if (invalidatesCandidateContext(response, binding)) {
            return new AssistantConversationFocus(
                    "none",
                    response.intentId(),
                    response.actionDraft() == null ? "none" : response.actionDraft().type(),
                    response.entities(),
                    null,
                    null
            );
        }

        if (previous != null && previous.hasCandidateContext()) {
            return previous;
        }

        return new AssistantConversationFocus(
                "none",
                response.intentId(),
                response.actionDraft() == null ? "none" : response.actionDraft().type(),
                response.entities(),
                null,
                null
        );
    }

    public boolean hasCandidateContext() {
        return candidateBinding != null && !candidateBinding.candidates().isEmpty();
    }

    public boolean hasSingleCandidateFocus() {
        return selectedCandidate != null || candidateCount() == 1;
    }

    public CandidateItem effectiveCandidate() {
        if (selectedCandidate != null) {
            return selectedCandidate;
        }
        if (candidateBinding != null && candidateBinding.candidates().size() == 1) {
            return candidateBinding.candidates().getFirst();
        }
        return null;
    }

    public CandidateBindingResult selectedBinding(String message) {
        CandidateItem candidate = effectiveCandidate();
        if (candidate == null || candidateBinding == null) {
            return null;
        }
        return selectedBinding(candidate, message);
    }

    public CandidateBindingResult selectedBinding(int oneBasedIndex, String message) {
        if (candidateBinding == null || oneBasedIndex <= 0) {
            return null;
        }
        CandidateBindingResult selected = candidateBinding.select(oneBasedIndex - 1);
        if (!"selected_candidate".equals(selected.status())) {
            return selected;
        }
        return selectedBinding(selected.selectedCandidate(), message);
    }

    private CandidateBindingResult selectedBinding(CandidateItem candidate, String message) {
        List<CandidateItem> candidates = candidateBinding.candidates();
        int index = candidates.indexOf(candidate);
        int oneBasedIndex = index < 0 ? 1 : index + 1;
        return new CandidateBindingResult(
                "selected_candidate",
                candidateBinding.source(),
                candidateBinding.query(),
                candidateBinding.candidateType(),
                candidates,
                message == null || message.isBlank()
                        ? "已根据上一轮上下文锁定候选，等待用户确认。"
                        : message,
                candidate,
                oneBasedIndex
        );
    }

    public int candidateCount() {
        return candidateBinding == null ? 0 : candidateBinding.candidates().size();
    }

    private static boolean invalidatesCandidateContext(
            IntentRecognitionResponse response,
            CandidateBindingResult binding
    ) {
        if (response.actionPlan() != null && "collection".equals(response.actionPlan().planKind())) {
            return true;
        }
        if (binding == null) {
            return false;
        }
        return List.of(
                "no_candidates",
                "missing_query",
                "unsupported_filter",
                "storage_api_not_configured",
                "missing_authorization",
                "storage_api_error",
                "collection_filter_only",
                "collection_not_executable"
        ).contains(binding.status());
    }

    private static CandidateItem selectedCandidate(CandidateBindingResult binding) {
        if (binding.selectedCandidate() != null) {
            return binding.selectedCandidate();
        }
        if (binding.candidates().size() == 1) {
            return binding.candidates().getFirst();
        }
        return null;
    }
}
