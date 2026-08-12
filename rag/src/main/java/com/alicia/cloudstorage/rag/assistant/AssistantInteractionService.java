package com.alicia.cloudstorage.rag.assistant;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AssistantInteractionService {

    public AssistantInteraction build(IntentRecognitionResponse response) {
        if (response == null) {
            return AssistantInteraction.idle();
        }

        SemanticFrame frame = response.semanticFrame() == null ? SemanticFrame.empty() : response.semanticFrame();
        if (frame.needsClarification()
                || !response.missingSlots().isEmpty()
                || "ask_clarification".equals(response.nextAction())) {
            return new AssistantInteraction(
                    "NEED_CLARIFICATION",
                    suggestionActions(frame.clarification()),
                    frame.clarification()
            );
        }

        CandidateBindingResult binding = response.candidateBinding();
        if (binding != null && List.of("multiple_candidates", "candidate_selection_out_of_range").contains(binding.status())) {
            return new AssistantInteraction(
                    "NEED_CANDIDATE_SELECTION",
                    candidateActions(binding),
                    SemanticFrame.Clarification.empty()
            );
        }
        if (binding != null && List.of(
                "no_candidates",
                "missing_authorization",
                "storage_api_not_configured",
                "storage_api_error",
                "missing_query"
        ).contains(binding.status())) {
            return new AssistantInteraction("BLOCKED", List.of(), SemanticFrame.Clarification.empty());
        }

        ActionPlan plan = response.actionPlan();
        BackendActionDraft backendDraft = response.backendActionDraft();
        String planStatus = plan == null || plan.status() == null ? "" : plan.status();
        if ("client_input_required".equals(planStatus)
                && plan.requiredClientFields() != null
                && !plan.requiredClientFields().isEmpty()) {
            return new AssistantInteraction(
                    "NEED_CLIENT_INPUT",
                    List.of(new AssistantInteraction.AllowedAction(
                            "SELECT_CLIENT_FILES",
                            "选择文件",
                            Map.of("requiredFields", plan.requiredClientFields())
                    )),
                    SemanticFrame.Clarification.empty()
            );
        }

        boolean readyForReview = List.of(
                "review_required",
                "collection_review_required",
                "ready_to_execute",
                "client_input_required"
        ).contains(planStatus)
                || binding != null && List.of("single_candidate", "selected_candidate").contains(binding.status())
                || backendDraft != null && List.of("backend_action_ready", "client_action_required").contains(backendDraft.status());
        if (readyForReview) {
            String actionType = plan == null || plan.actionType() == null ? "" : plan.actionType();
            boolean upload = "file.upload".equals(actionType)
                    || "composite.create_folder_then_upload".equals(actionType);
            return new AssistantInteraction(
                    "NEED_CONFIRMATION",
                    List.of(new AssistantInteraction.AllowedAction(
                            upload ? "CONFIRM_UPLOAD" : "CONFIRM_PLAN",
                            upload ? "确认上传" : "确认计划",
                            Map.of()
                    )),
                    SemanticFrame.Clarification.empty()
            );
        }

        if (binding != null && "search_results_ready".equals(binding.status())) {
            return new AssistantInteraction("COMPLETED", List.of(), SemanticFrame.Clarification.empty());
        }
        if ("respond_only".equals(response.nextAction()) || "show_search_results".equals(response.nextAction())) {
            return new AssistantInteraction("COMPLETED", List.of(), SemanticFrame.Clarification.empty());
        }
        return AssistantInteraction.idle();
    }

    private List<AssistantInteraction.AllowedAction> candidateActions(CandidateBindingResult binding) {
        List<AssistantInteraction.AllowedAction> actions = new ArrayList<>();
        for (int index = 0; index < binding.candidates().size(); index++) {
            CandidateItem candidate = binding.candidates().get(index);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("candidateIndex", index + 1);
            if (candidate.nodeId() != null) {
                payload.put("candidateId", candidate.nodeId());
            }
            actions.add(new AssistantInteraction.AllowedAction(
                    "SELECT_CANDIDATE",
                    candidate.name(),
                    payload
            ));
        }
        return List.copyOf(actions);
    }

    private List<AssistantInteraction.AllowedAction> suggestionActions(SemanticFrame.Clarification clarification) {
        if (clarification == null || clarification.suggestions().isEmpty()) {
            return List.of();
        }
        return clarification.suggestions().stream()
                .map(suggestion -> new AssistantInteraction.AllowedAction(
                        "SEND_SUGGESTION",
                        suggestion,
                        Map.of("message", suggestion)
                ))
                .toList();
    }
}
