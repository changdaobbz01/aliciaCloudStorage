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
        String query = bindingQuery(intent, response);
        return candidateSearchPort.search(new CandidateSearchRequest(
                response.intentId(),
                intent.actionType(),
                intent.candidateType(),
                query,
                maxResults,
                authorizationHeader == null ? "" : authorizationHeader.trim()
        ));
    }

    private String bindingQuery(IntentRouter.IntentDefinition intent, IntentRecognitionResponse response) {
        Object target = "FOLDER".equalsIgnoreCase(intent.candidateType())
                ? response.entities().get("target_folder")
                : response.entities().get("target_name");
        if (target != null && !String.valueOf(target).isBlank()) {
            return String.valueOf(target).trim();
        }
        if (response.normalizedQuery() != null && !response.normalizedQuery().isBlank()) {
            return response.normalizedQuery().trim();
        }
        return response.message() == null ? "" : response.message().trim();
    }
}
