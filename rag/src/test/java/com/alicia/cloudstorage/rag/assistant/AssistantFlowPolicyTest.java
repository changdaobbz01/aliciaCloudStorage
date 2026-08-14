package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantFlowPolicyTest {

    @Test
    void semanticClarificationBlocksPlanningAndBackendDraftGeneration() {
        RagConfigLoader configLoader = new RagConfigLoader(new ObjectMapper());
        IntentRouter intentRouter = new IntentRouter(configLoader, new EntityExtractor(configLoader));
        IntentRecognitionService recognitionService = new IntentRecognitionService(
                message -> Optional.empty(),
                intentRouter,
                configLoader
        );
        IntentRecognitionResponse ready = recognitionService.recognize("分享合同.pdf");
        SemanticFrame blockedFrame = new SemanticFrame(
                SemanticFrame.VERSION,
                "NEW_TASK",
                "SHARE",
                ready.semanticFrame().query(),
                ready.semanticFrame().scope(),
                ready.semanticFrame().reference(),
                1.0,
                List.of("batch_share_unsupported"),
                new SemanticFrame.Clarification(
                        "batch_share_unsupported",
                        "目前一次只能分享一个文件。",
                        List.of("分享合同.pdf")
                )
        );
        IntentRecognitionResponse contradictory = ready.withSemanticFrame(
                blockedFrame,
                ready.entities(),
                ready.actionDraft()
        );
        CandidateItem selected = new CandidateItem(
                7L,
                1L,
                "合同.pdf",
                "FILE",
                10L,
                "pdf",
                "application/pdf",
                ""
        );
        CandidateBindingResult binding = new CandidateBindingResult(
                "selected_candidate",
                "test",
                "合同.pdf",
                "FILE",
                List.of(selected),
                "已选择合同.pdf",
                selected,
                1
        );

        ActionPlan plan = new ActionPlanService(configLoader).build(contradictory);
        BackendActionDraft backendDraft = new BackendActionDraftService(configLoader).build(
                contradictory,
                binding,
                true,
                AssistantClientContext.empty(),
                "Bearer test"
        );

        assertThat(plan.status()).isEqualTo("clarification_required");
        assertThat(backendDraft.status()).isEqualTo("waiting_for_clarification");
        assertThat(backendDraft.executableByBackend()).isFalse();
    }
}
