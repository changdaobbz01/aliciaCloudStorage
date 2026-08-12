package com.alicia.cloudstorage.rag.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CandidateBindingService {

    private final CandidateSearchPort candidateSearchPort;
    private final IntentRouter intentRouter;
    private final int maxResults;

    public CandidateBindingService(
            CandidateSearchPort candidateSearchPort,
            IntentRouter intentRouter,
            @Value("${alicia.rag.candidate-binding.max-results:5}") int maxResults
    ) {
        this.candidateSearchPort = candidateSearchPort;
        this.intentRouter = intentRouter;
        this.maxResults = Math.max(1, maxResults);
    }

    public CandidateBindingResult bind(IntentRecognitionResponse response, String authorizationHeader) {
        if (response == null) {
            return CandidateBindingResult.skipped("not_requested", "没有可绑定的识别结果。");
        }
        String actionType = response.actionDraft() == null ? "none" : response.actionDraft().type();
        if (actionType == null || actionType.isBlank() || "none".equals(actionType)) {
            return CandidateBindingResult.skipped("not_requested", "当前回复不需要候选绑定。");
        }
        if (!response.missingSlots().isEmpty() || "ask_clarification".equals(response.nextAction())) {
            return CandidateBindingResult.skipped("waiting_for_clarification", "仍有缺失信息，暂不查询真实候选。");
        }
        if (!"wait_for_backend_binding".equals(response.nextAction())) {
            return CandidateBindingResult.skipped("not_requested", "当前步骤不需要候选绑定。");
        }

        IntentRouter.IntentDefinition intent = intentRouter.getIntent(response.intentId());
        if ("NONE".equalsIgnoreCase(intent.candidateType())) {
            return CandidateBindingResult.skipped(
                    "collection_filter_only",
                    "当前意图使用集合筛选条件生成 ActionPlan，暂不执行单对象候选绑定。"
            );
        }
        QueryRoleAndValue query = bindingQuery(intent, response);
        return candidateSearchPort.search(new CandidateSearchRequest(
                response.intentId(),
                intent.actionType(),
                intent.candidateType(),
                query.role(),
                query.value(),
                maxResults,
                authorizationHeader == null ? "" : authorizationHeader.trim()
        ));
    }

    private QueryRoleAndValue bindingQuery(IntentRouter.IntentDefinition intent, IntentRecognitionResponse response) {
        String role = "FOLDER".equalsIgnoreCase(intent.candidateType()) ? "target_folder" : "target_name";
        Object target = response.entities().get(role);
        if (target != null && !String.valueOf(target).isBlank()) {
            return new QueryRoleAndValue(role, TextSupport.sanitizeNodeName(String.valueOf(target)));
        }
        if (response.normalizedQuery() != null && !response.normalizedQuery().isBlank()) {
            return new QueryRoleAndValue("search_query", TextSupport.sanitizeNodeName(response.normalizedQuery()));
        }
        return new QueryRoleAndValue("message", TextSupport.sanitizeNodeName(response.message()));
    }

    private record QueryRoleAndValue(
            String role,
            String value
    ) {
    }
}
