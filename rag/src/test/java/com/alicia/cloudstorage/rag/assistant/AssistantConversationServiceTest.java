package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantConversationServiceTest {

    private AssistantConversationService conversationService;
    private RagConfigLoader configLoader;
    private IntentRouter intentRouter;
    private IntentRecognitionService intentRecognitionService;

    @BeforeEach
    void setUp() {
        configLoader = new RagConfigLoader(new ObjectMapper());
        intentRouter = new IntentRouter(configLoader, new EntityExtractor(configLoader));
        IntentModelClient unavailableClient = message -> Optional.empty();
        intentRecognitionService = new IntentRecognitionService(
                unavailableClient,
                intentRouter,
                configLoader
        );
        conversationService = conversationServiceWith(request -> CandidateBindingResult.skipped(
                "test_skipped",
                "测试环境不查询真实候选。"
        ));
    }

    private AssistantConversationService conversationServiceWith(CandidateSearchPort candidateSearchPort) {
        return conversationServiceWith(
                candidateSearchPort,
                request -> CollectionPreviewResult.skipped("not_requested", "测试环境不执行集合预览。")
        );
    }

    private AssistantConversationService conversationServiceWith(
            CandidateSearchPort candidateSearchPort,
            CollectionPreviewPort collectionPreviewPort
    ) {
        return conversationServiceWith(candidateSearchPort, collectionPreviewPort, intentRecognitionService);
    }

    private AssistantConversationService conversationServiceWith(
            CandidateSearchPort candidateSearchPort,
            CollectionPreviewPort collectionPreviewPort,
            IntentRecognitionService recognitionService
    ) {
        CandidateBindingService candidateBindingService = new CandidateBindingService(candidateSearchPort, intentRouter, 5);
        ConversationContextResolver contextResolver = new ConversationContextResolver(
                (message, conversation, baseResponse) -> Optional.empty(),
                recognitionService,
                configLoader
        );
        CollectionActionSnapshotStore snapshotStore = new CollectionActionSnapshotStore(30, 1000);
        return new AssistantConversationService(
                recognitionService,
                intentRouter,
                new AssistantConversationStore(30, 100),
                candidateBindingService,
                new CandidateSelectionService(configLoader),
                contextResolver,
                new BackendActionDraftService(configLoader, snapshotStore),
                new ActionPlanService(configLoader),
                new CollectionPreviewService(collectionPreviewPort, snapshotStore, 20, 500),
                new CollectionOperationSelectorResolver(),
                new NavigationOperationResolver(configLoader),
                new ScopedCollectionPlanningService(
                        candidateSearchPort,
                        collectionPreviewPort,
                        snapshotStore,
                        20,
                        500
                ),
                new ExecutableConstraintGuard(),
                configLoader
        );
    }

    @Test
    void fillsPendingDeleteTargetFromNextTurn() {
        IntentRecognitionResponse firstTurn = conversationService.plan(new AssistantPlanRequest("删除", ""));

        assertThat(firstTurn.intentId()).isEqualTo("file_delete");
        assertThat(firstTurn.nextAction()).isEqualTo("ask_clarification");
        assertThat(firstTurn.missingSlots()).containsExactly("target_name");
        assertThat(firstTurn.conversation().conversationId()).isNotBlank();
        assertThat(firstTurn.conversation().status()).isEqualTo("waiting_for_clarification");

        IntentRecognitionResponse secondTurn = conversationService.plan(new AssistantPlanRequest(
                "临时截图",
                firstTurn.conversation().conversationId()
        ));

        assertThat(secondTurn.intentId()).isEqualTo("file_delete");
        assertThat(secondTurn.missingSlots()).isEmpty();
        assertThat(secondTurn.nextAction()).isEqualTo("wait_for_backend_binding");
        assertThat(secondTurn.entities()).containsEntry("target_name", "临时截图");
        assertThat(secondTurn.actionDraft().parameters()).containsEntry("target_name", "临时截图");
        assertThat(secondTurn.conversation().conversationId()).isEqualTo(firstTurn.conversation().conversationId());
        assertThat(secondTurn.conversation().turnIndex()).isEqualTo(2);
    }

    @Test
    void genericMutationNounsDoNotBecomePreviousResultCollections() {
        IntentRecognitionResponse delete = conversationService.plan(new AssistantPlanRequest("删除文件", ""));
        IntentRecognitionResponse move = conversationService.plan(new AssistantPlanRequest("把文件移动到资料", ""));

        assertThat(delete.intentId()).isEqualTo("file_delete");
        assertThat(delete.semanticFrame().query().mode()).isEqualTo("NONE");
        assertThat(delete.semanticFrame().reference().type()).isEqualTo("NONE");
        assertThat(delete.missingSlots()).contains("target_name");
        assertThat(delete.actionDraft().type()).isEqualTo("none");

        assertThat(move.intentId()).isEqualTo("node_move");
        assertThat(move.semanticFrame().query().mode()).isEqualTo("NONE");
        assertThat(move.semanticFrame().reference().type()).isEqualTo("NONE");
        assertThat(move.entities()).containsEntry("target_folder", "资料");
        assertThat(move.missingSlots()).contains("target_name").doesNotContain("target_folder");
        assertThat(move.actionDraft().type()).isEqualTo("none");
    }

    @Test
    void explicitNewIntentDoesNotUsePendingPreviousIntent() {
        IntentRecognitionResponse firstTurn = conversationService.plan(new AssistantPlanRequest("删除", ""));

        IntentRecognitionResponse secondTurn = conversationService.plan(new AssistantPlanRequest(
                "分享合同",
                firstTurn.conversation().conversationId()
        ));

        assertThat(secondTurn.intentId()).isEqualTo("file_share");
        assertThat(secondTurn.safety().risk()).isEqualTo("medium");
        assertThat(secondTurn.actionDraft().type()).isEqualTo("share");
        assertThat(secondTurn.entities()).containsEntry("target_name", "合同");
    }

    @Test
    void personaChatRespondsWithoutBackendBinding() {
        IntentRecognitionResponse response = conversationService.plan(new AssistantPlanRequest("你是谁", ""));

        assertThat(response.intentId()).isEqualTo("assistant_identity");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.conversation().status()).isEqualTo("responded");
        assertThat(response.candidateBinding().status()).isEqualTo("not_requested");
        assertThat(response.backendActionDraft().status()).isEqualTo("not_requested");
        assertThat(response.actionPlan().status()).isEqualTo("completed");
        assertThat(response.actionPlan().actionType()).isEqualTo("none");
        assertThat(response.actionPlan().steps()).isEmpty();
    }

    @Test
    void fallbackClarificationKeepsMessageOnlyActionPlan() {
        IntentRecognitionResponse response = conversationService.plan(new AssistantPlanRequest("今天的风有点大", ""));

        assertThat(response.intentId()).isEqualTo("fallback");
        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.actionDraft().needsBackendBinding()).isFalse();
        assertThat(response.candidateBinding().status()).isEqualTo("not_requested");
        assertThat(response.backendActionDraft().status()).isEqualTo("not_requested");
        assertThat(response.actionPlan().status()).isEqualTo("completed");
        assertThat(response.actionPlan().actionType()).isEqualTo("none");
        assertThat(response.actionPlan().steps()).isEmpty();
        assertThat(response.conversation().status()).isEqualTo("waiting_for_clarification");
    }

    @Test
    void capabilityExamplesRespondWithoutBackendBinding() {
        IntentRecognitionResponse response = conversationService.plan(new AssistantPlanRequest("详细举例，看看你的能力项", ""));

        assertThat(response.intentId()).isEqualTo("assistant_capability_examples");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.candidateBinding().status()).isEqualTo("not_requested");
        assertThat(response.backendActionDraft().status()).isEqualTo("not_requested");
        assertThat(response.actionPlan().status()).isEqualTo("completed");
        assertThat(response.actionPlan().actionType()).isEqualTo("none");
        assertThat(response.assistantText()).contains("比如");
        assertThat(response.conversation().status()).isEqualTo("responded");
    }

    @Test
    void casualAcknowledgementRespondsWithoutBackendBinding() {
        IntentRecognitionResponse response = conversationService.plan(new AssistantPlanRequest("好吧，了解了", ""));

        assertThat(response.intentId()).isEqualTo("assistant_acknowledgement");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.candidateBinding().status()).isEqualTo("not_requested");
        assertThat(response.backendActionDraft().status()).isEqualTo("not_requested");
        assertThat(response.actionPlan().status()).isEqualTo("completed");
        assertThat(response.conversation().status()).isEqualTo("responded");
    }

    @Test
    void casualAcknowledgementDoesNotConfirmPendingMutation() {
        IntentRecognitionResponse firstTurn = conversationService.plan(new AssistantPlanRequest("删除临时截图", ""));

        IntentRecognitionResponse secondTurn = conversationService.plan(new AssistantPlanRequest(
                "好的",
                firstTurn.conversation().conversationId()
        ));

        assertThat(firstTurn.intentId()).isEqualTo("file_delete");
        assertThat(secondTurn.intentId()).isEqualTo("assistant_acknowledgement");
        assertThat(secondTurn.actionDraft().type()).isEqualTo("none");
        assertThat(secondTurn.backendActionDraft().status()).isEqualTo("not_requested");
        assertThat(secondTurn.safety().requiresConfirmation()).isFalse();
    }

    @Test
    void confirmMessagePreservesPendingActionContext() {
        IntentRecognitionResponse firstTurn = conversationService.plan(new AssistantPlanRequest("删除临时截图", ""));

        assertThat(firstTurn.intentId()).isEqualTo("file_delete");
        assertThat(firstTurn.missingSlots()).isEmpty();

        IntentRecognitionResponse secondTurn = conversationService.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId()
        ));

        assertThat(secondTurn.intentId()).isEqualTo("file_delete");
        assertThat(secondTurn.entities()).containsEntry("target_name", "临时截图");
        assertThat(secondTurn.actionDraft().type()).isEqualTo("delete");
        assertThat(secondTurn.nextAction()).isEqualTo("wait_for_backend_binding");
    }

    @Test
    void selectsCandidateOnlyFromPreviousBindingSnapshot() {
        MultipleCandidateSearchPort port = new MultipleCandidateSearchPort();
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest("删除临时截图", ""), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "第二个",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(firstTurn.candidateBinding().status()).isEqualTo("multiple_candidates");
        assertThat(secondTurn.intentId()).isEqualTo("file_delete");
        assertThat(secondTurn.entities()).containsEntry("target_name", "临时截图");
        assertThat(secondTurn.actionDraft().parameters()).doesNotContainKeys("nodeId", "node_id", "fileId", "file_id");
        assertThat(secondTurn.candidateBinding().status()).isEqualTo("selected_candidate");
        assertThat(secondTurn.candidateBinding().selectedIndex()).isEqualTo(2);
        assertThat(secondTurn.candidateBinding().selectedCandidate().nodeId()).isEqualTo(102L);
        assertThat(secondTurn.nextAction()).isEqualTo("wait_for_user_confirmation");
        assertThat(secondTurn.conversation().status()).isEqualTo("waiting_for_user_confirmation");
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void outOfRangeCandidateSelectionKeepsPreviousCandidates() {
        MultipleCandidateSearchPort port = new MultipleCandidateSearchPort();
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest("删除临时截图", ""), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "第九个",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(secondTurn.intentId()).isEqualTo("file_delete");
        assertThat(secondTurn.candidateBinding().status()).isEqualTo("candidate_selection_out_of_range");
        assertThat(secondTurn.candidateBinding().candidates()).hasSize(2);
        assertThat(secondTurn.candidateBinding().selectedCandidate()).isNull();
        assertThat(secondTurn.nextAction()).isEqualTo("wait_for_candidate_selection");
        assertThat(secondTurn.conversation().status()).isEqualTo("waiting_for_candidate_selection");
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void confirmAfterCandidateSelectionReusesStoredCandidateBinding() {
        MultipleCandidateSearchPort port = new MultipleCandidateSearchPort();
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest("删除临时截图", ""), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "选第2个",
                firstTurn.conversation().conversationId()
        ), "Bearer token");
        IntentRecognitionResponse thirdTurn = service.plan(new AssistantPlanRequest(
                "确认",
                secondTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(thirdTurn.intentId()).isEqualTo("file_delete");
        assertThat(thirdTurn.candidateBinding().status()).isEqualTo("selected_candidate");
        assertThat(thirdTurn.candidateBinding().selectedCandidate().nodeId()).isEqualTo(102L);
        assertThat(thirdTurn.nextAction()).isEqualTo("handoff_to_backend");
        assertThat(thirdTurn.conversation().status()).isEqualTo("backend_action_ready");
        assertThat(thirdTurn.backendActionDraft().status()).isEqualTo("backend_action_ready");
        assertThat(thirdTurn.backendActionDraft().method()).isEqualTo("DELETE");
        assertThat(thirdTurn.backendActionDraft().path()).isEqualTo("/api/storage/nodes/102");
        assertThat(thirdTurn.backendActionDraft().targetCandidate().nodeId()).isEqualTo(102L);
        assertThat(thirdTurn.actionPlan().status()).isEqualTo("ready_to_execute");
        assertThat(thirdTurn.actionPlan().actionType()).isEqualTo("node.trash");
        assertThat(thirdTurn.actionPlan().bindings()).containsKey("targetNode");
        assertThat(thirdTurn.actionPlan().steps()).hasSize(1);
        assertThat(thirdTurn.actionPlan().steps().getFirst().action()).isEqualTo("node.trash");
        assertThat(thirdTurn.actionPlan().steps().getFirst().params()).containsEntry("nodeId", 102L);
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void searchResultsAreReadyToShowWithoutCandidateSelection() {
        SearchResultsPort port = new SearchResultsPort();
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse response = service.plan(new AssistantPlanRequest("找合同", ""), "Bearer token");

        assertThat(response.intentId()).isEqualTo("file_search");
        assertThat(response.candidateBinding().status()).isEqualTo("search_results_ready");
        assertThat(response.candidateBinding().candidates()).hasSize(2);
        assertThat(response.nextAction()).isEqualTo("show_search_results");
        assertThat(response.conversation().status()).isEqualTo("search_results_ready");
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void answersFollowUpQuestionFromPreviousSingleSearchCandidate() {
        SingleSearchResultPort port = new SingleSearchResultPort(
                new CandidateItem(701L, null, "codex_ui.xml", "FILE", 2048L, "xml", "application/xml", "2026-08-11T13:29:42")
        );
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "帮我找一下名字中有codex的文件",
                ""
        ), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "它是什么格式的文件",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(firstTurn.candidateBinding().status()).isEqualTo("search_results_ready");
        assertThat(secondTurn.intentId()).isEqualTo("assistant_file_context_question");
        assertThat(secondTurn.nextAction()).isEqualTo("respond_only");
        assertThat(secondTurn.assistantText()).contains("codex_ui.xml").contains("XML");
        assertThat(secondTurn.candidateBinding().status()).isEqualTo("not_requested");
        assertThat(secondTurn.conversation().status()).isEqualTo("responded");
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void answersLocationFollowUpFromPreviousCandidateWithoutSearchingAgain() {
        SingleSearchResultPort port = new SingleSearchResultPort(
                new CandidateItem(
                        703L,
                        70L,
                        "测试图片.png",
                        "FILE",
                        2048L,
                        "png",
                        "image/png",
                        "2026-08-13T10:00:00",
                        "/文件记录/测试图片.png",
                        List.of()
                )
        );
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse firstTurn = service.plan(
                new AssistantPlanRequest("查找测试图片", ""),
                "Bearer token"
        );
        IntentRecognitionResponse secondTurn = service.plan(
                new AssistantPlanRequest("这个文件在哪", firstTurn.conversation().conversationId()),
                "Bearer token"
        );

        assertThat(secondTurn.intentId()).isEqualTo("assistant_file_context_question");
        assertThat(secondTurn.semanticFrame().relation()).isEqualTo("FOLLOW_UP");
        assertThat(secondTurn.assistantText()).contains("测试图片.png").contains("/文件记录/测试图片.png");
        assertThat(secondTurn.candidateBinding().status()).isEqualTo("not_requested");
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void answersFollowUpQuestionFromOrdinalSearchCandidate() {
        SearchResultsPort port = new SearchResultsPort();
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "帮我找一下合同",
                ""
        ), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "第二个是什么格式",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(firstTurn.candidateBinding().status()).isEqualTo("search_results_ready");
        assertThat(secondTurn.intentId()).isEqualTo("assistant_file_context_question");
        assertThat(secondTurn.nextAction()).isEqualTo("respond_only");
        assertThat(secondTurn.assistantText()).contains("合同扫描件.pdf").contains("PDF");
        assertThat(secondTurn.entities()).containsEntry("target_name", "合同扫描件.pdf");
        assertThat(secondTurn.candidateBinding().status()).isEqualTo("not_requested");
        assertThat(secondTurn.conversation().status()).isEqualTo("responded");
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void rewritesMutationFollowUpAndReusesPreviousCandidateBinding() {
        SingleSearchResultPort port = new SingleSearchResultPort(
                new CandidateItem(702L, null, "codex_ui.xml", "FILE", 2048L, "xml", "application/xml", "")
        );
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "查找 codex",
                ""
        ), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "把它删了",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(firstTurn.candidateBinding().status()).isEqualTo("search_results_ready");
        assertThat(secondTurn.intentId()).isEqualTo("file_delete");
        assertThat(secondTurn.entities()).containsEntry("target_name", "codex_ui.xml");
        assertThat(secondTurn.candidateBinding().status()).isEqualTo("selected_candidate");
        assertThat(secondTurn.candidateBinding().selectedCandidate().nodeId()).isEqualTo(702L);
        assertThat(secondTurn.nextAction()).isEqualTo("wait_for_user_confirmation");
        assertThat(secondTurn.conversation().status()).isEqualTo("waiting_for_user_confirmation");
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void mutationFollowUpPerformsOnlyOneSemanticRecognitionPerTurn() {
        AtomicInteger recognitionCalls = new AtomicInteger();
        IntentModelClient countingUnavailableClient = request -> {
            recognitionCalls.incrementAndGet();
            return Optional.empty();
        };
        IntentRecognitionService countingRecognitionService = new IntentRecognitionService(
                countingUnavailableClient,
                intentRouter,
                configLoader
        );
        SingleSearchResultPort port = new SingleSearchResultPort(
                new CandidateItem(703L, null, "codex_ui.xml", "FILE", 2048L, "xml", "application/xml", "")
        );
        AssistantConversationService service = conversationServiceWith(
                port,
                request -> CollectionPreviewResult.skipped("not_requested", "测试环境不执行集合预览。"),
                countingRecognitionService
        );

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest("查找 codex", ""), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "把它删了",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(secondTurn.intentId()).isEqualTo("file_delete");
        assertThat(secondTurn.candidateBinding().selectedCandidate().nodeId()).isEqualTo(703L);
        assertThat(recognitionCalls).hasValue(2);
    }

    @Test
    void rewritesOrdinalMutationFollowUpAndReusesSelectedCandidateBinding() {
        SearchResultsPort port = new SearchResultsPort();
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "查找合同",
                ""
        ), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "把第二个分享一下",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(firstTurn.candidateBinding().status()).isEqualTo("search_results_ready");
        assertThat(secondTurn.intentId()).isEqualTo("file_share");
        assertThat(secondTurn.entities()).containsEntry("target_name", "合同扫描件.pdf");
        assertThat(secondTurn.candidateBinding().status()).isEqualTo("selected_candidate");
        assertThat(secondTurn.candidateBinding().selectedIndex()).isEqualTo(2);
        assertThat(secondTurn.candidateBinding().selectedCandidate().nodeId()).isEqualTo(202L);
        assertThat(secondTurn.nextAction()).isEqualTo("wait_for_user_confirmation");
        assertThat(secondTurn.conversation().status()).isEqualTo("waiting_for_user_confirmation");
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void searchWithoutCandidatesKeepsNoCandidateState() {
        NoCandidateSearchPort port = new NoCandidateSearchPort();
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse response = service.plan(new AssistantPlanRequest("找项目文档”", ""), "Bearer token");

        assertThat(response.intentId()).isEqualTo("file_search");
        assertThat(port.lastRequest.query()).isEqualTo("项目文档");
        assertThat(response.candidateBinding().status()).isEqualTo("no_candidates");
        assertThat(response.candidateBinding().candidates()).isEmpty();
        assertThat(response.actionPlan().status()).isEqualTo("binding_required");
        assertThat(response.conversation().status()).isEqualTo("waiting_for_backend_binding");
    }


    @Test
    void confirmSingleRenameCandidateBuildsBackendActionDraft() {
        SingleCandidateSearchPort port = new SingleCandidateSearchPort(301L, "项目计划.docx");
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "把项目计划重命名为 项目计划-最终版.docx",
                ""
        ), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(firstTurn.candidateBinding().status()).isEqualTo("single_candidate");
        assertThat(secondTurn.nextAction()).isEqualTo("handoff_to_backend");
        assertThat(secondTurn.backendActionDraft().status()).isEqualTo("backend_action_ready");
        assertThat(secondTurn.backendActionDraft().method()).isEqualTo("PUT");
        assertThat(secondTurn.backendActionDraft().path()).isEqualTo("/api/storage/nodes/301/rename");
        assertThat(secondTurn.backendActionDraft().body()).containsEntry("name", "项目计划-最终版.docx");
        assertThat(secondTurn.backendActionDraft().pathVariables()).containsEntry("nodeId", 301L);
        assertThat(secondTurn.actionDraft().parameters()).doesNotContainKeys("nodeId", "node_id");
    }

    @Test
    void confirmSingleShareCandidateBuildsShareLinkDraft() {
        SingleCandidateSearchPort port = new SingleCandidateSearchPort(401L, "合同.pdf");
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest("分享合同", ""), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(secondTurn.nextAction()).isEqualTo("handoff_to_backend");
        assertThat(secondTurn.backendActionDraft().status()).isEqualTo("backend_action_ready");
        assertThat(secondTurn.backendActionDraft().method()).isEqualTo("POST");
        assertThat(secondTurn.backendActionDraft().path()).isEqualTo("/api/share-links");
        assertThat(secondTurn.backendActionDraft().body()).containsEntry("title", "合同.pdf");
        assertThat(secondTurn.backendActionDraft().body()).containsEntry("allowDownload", true);
        assertThat(secondTurn.backendActionDraft().body()).containsEntry("allowSave", true);
        assertThat(secondTurn.backendActionDraft().body().get("nodeIds")).isEqualTo(List.of(401L));
    }

    @Test
    void confirmUploadTargetBuildsClientUploadDraft() {
        SingleCandidateSearchPort port = new SingleCandidateSearchPort(501L, "项目资料");
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest("上传到项目资料", ""), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(firstTurn.intentId()).isEqualTo("file_upload");
        assertThat(secondTurn.nextAction()).isEqualTo("handoff_to_client_upload");
        assertThat(secondTurn.conversation().status()).isEqualTo("client_action_required");
        assertThat(secondTurn.backendActionDraft().status()).isEqualTo("client_action_required");
        assertThat(secondTurn.backendActionDraft().executableByBackend()).isFalse();
        assertThat(secondTurn.backendActionDraft().method()).isEqualTo("POST");
        assertThat(secondTurn.backendActionDraft().path()).isEqualTo("/api/storage/files");
        assertThat(secondTurn.backendActionDraft().contentType()).isEqualTo("multipart/form-data");
        assertThat(secondTurn.backendActionDraft().queryParameters()).containsEntry("parentId", 501L);
        assertThat(secondTurn.backendActionDraft().requiredClientFields()).containsExactly("files");
        assertThat(secondTurn.actionPlan().status()).isEqualTo("client_input_required");
        assertThat(secondTurn.actionPlan().actionType()).isEqualTo("file.upload");
        assertThat(secondTurn.actionPlan().requiredClientFields()).containsExactly("files");
        assertThat(secondTurn.actionPlan().steps().getFirst().params()).containsEntry("parentId", 501L);
    }

    @Test
    void structuredUploadCandidateSelectionAdvancesToConfirmationWithoutLooping() {
        CandidateSearchPort port = request -> new CandidateBindingResult(
                "multiple_candidates",
                "test",
                request.query(),
                "FOLDER",
                List.of(
                        new CandidateItem(501L, null, "测试目录", "FOLDER", 0L, "", "", ""),
                        new CandidateItem(502L, 501L, "测试目录2", "FOLDER", 0L, "", "", "")
                ),
                "匹配到多个候选，需要用户选择。"
        );
        AssistantConversationService service = conversationServiceWith(port);
        AssistantClientContext clientContext = new AssistantClientContext(null, "/", Map.of("files", 1));

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "将这个文件上传到测试目录",
                "",
                clientContext
        ), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "选第1个",
                firstTurn.conversation().conversationId(),
                clientContext,
                new AssistantClientEvent("SELECT_CANDIDATE", 501L, 1)
        ), "Bearer token");

        assertThat(firstTurn.interaction().stage()).isEqualTo("NEED_CANDIDATE_SELECTION");
        assertThat(secondTurn.candidateBinding().status()).isEqualTo("selected_candidate");
        assertThat(secondTurn.candidateBinding().selectedCandidate().nodeId()).isEqualTo(501L);
        assertThat(secondTurn.actionPlan().status()).isEqualTo("review_required");
        assertThat(secondTurn.interaction().stage()).isEqualTo("NEED_CONFIRMATION");
        assertThat(secondTurn.interaction().allowedActions())
                .extracting(AssistantInteraction.AllowedAction::type)
                .containsExactly("CONFIRM_UPLOAD");
    }

    @Test
    void naturalLanguageCorrectionReusesPreviousSearchSemantics() {
        CandidateSearchRequest[] lastRequest = new CandidateSearchRequest[1];
        CandidateSearchPort port = request -> {
            lastRequest[0] = request;
            return new CandidateBindingResult(
                    "search_results_ready",
                    "test",
                    request.query(),
                    request.candidateType(),
                    List.of(new CandidateItem(601L, null, "测试目录", "FOLDER", 0L, "", "", "")),
                    "已完成文件检索。"
            );
        };
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse firstTurn = service.plan(
                new AssistantPlanRequest("你列出名字带测试的文件夹", ""),
                "Bearer token"
        );
        IntentRecognitionResponse secondTurn = service.plan(
                new AssistantPlanRequest("测试目录呢？", firstTurn.conversation().conversationId()),
                "Bearer token"
        );

        assertThat(secondTurn.semanticFrame().relation()).isEqualTo("CORRECTION");
        assertThat(secondTurn.semanticFrame().operation()).isEqualTo("SEARCH");
        assertThat(secondTurn.semanticFrame().query().resultType()).isEqualTo("FOLDER");
        assertThat(secondTurn.entities()).containsEntry("target_name", "测试目录");
        assertThat(lastRequest[0].query()).isEqualTo("测试目录");
        assertThat(lastRequest[0].candidateType()).isEqualTo("FOLDER");
    }

    @Test
    void browsesPreviousFolderThenResolvesOrdinalFileOperations() {
        List<CandidateSearchRequest> requests = new java.util.ArrayList<>();
        CandidateSearchPort port = request -> {
            requests.add(request);
            if (requests.size() == 1) {
                return new CandidateBindingResult(
                        "search_results_ready",
                        "test",
                        request.query(),
                        "FOLDER",
                        List.of(new CandidateItem(
                                801L, null, "测试目录", "FOLDER", 0L, "", "", "", "/测试目录", List.of()
                        )),
                        "找到测试目录。"
                );
            }
            return new CandidateBindingResult(
                    "search_results_ready",
                    "test",
                    request.query(),
                    "FILE",
                    List.of(
                            new CandidateItem(811L, 801L, "报告一.pdf", "FILE", 10L, "pdf", "application/pdf", ""),
                            new CandidateItem(812L, 801L, "报告二.docx", "FILE", 20L, "docx", "application/docx", "")
                    ),
                    "已列出测试目录中的文件。"
            );
        };
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse folder = service.plan(
                new AssistantPlanRequest("找到测试目录这个文件夹", ""),
                "Bearer token"
        );
        IntentRecognitionResponse contents = service.plan(
                new AssistantPlanRequest("这个文件夹中文件有哪些", folder.conversation().conversationId()),
                "Bearer token"
        );
        IntentRecognitionResponse delete = service.plan(
                new AssistantPlanRequest("删除第一个文件", contents.conversation().conversationId()),
                "Bearer token"
        );
        IntentRecognitionResponse deleteAnother = service.plan(
                new AssistantPlanRequest("删除另一个文件", delete.conversation().conversationId()),
                "Bearer token"
        );
        IntentRecognitionResponse rename = service.plan(
                new AssistantPlanRequest("重命名第一个文件", contents.conversation().conversationId()),
                "Bearer token"
        );
        IntentRecognitionResponse renamed = service.plan(
                new AssistantPlanRequest("改成最终报告.pdf", rename.conversation().conversationId()),
                "Bearer token"
        );

        assertThat(folder.semanticFrame().query().nameSurface()).isEqualTo("测试目录");
        assertThat(contents.semanticFrame().relation()).isEqualTo("FOLLOW_UP");
        assertThat(contents.semanticFrame().query().mode()).isEqualTo("LIST_CHILDREN");
        assertThat(contents.semanticFrame().scope().type()).isEqualTo("PREVIOUS_RESULTS");
        assertThat(contents.candidateBinding().candidates()).extracting(CandidateItem::name)
                .containsExactly("报告一.pdf", "报告二.docx");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).queryMode()).isEqualTo("directory_list");
        assertThat(requests.get(1).scope()).isEqualTo("current");
        assertThat(requests.get(1).currentFolderId()).isEqualTo(801L);

        assertThat(delete.intentId()).isEqualTo("file_delete");
        assertThat(delete.entities()).containsEntry("target_name", "报告一.pdf");
        assertThat(delete.candidateBinding().selectedCandidate().nodeId()).isEqualTo(811L);
        assertThat(delete.interaction().stage()).isEqualTo("NEED_CONFIRMATION");

        assertThat(deleteAnother.intentId()).isEqualTo("file_delete");
        assertThat(deleteAnother.entities()).containsEntry("target_name", "报告二.docx");
        assertThat(deleteAnother.candidateBinding().selectedCandidate().nodeId()).isEqualTo(812L);
        assertThat(deleteAnother.interaction().stage()).isEqualTo("NEED_CONFIRMATION");

        assertThat(rename.intentId()).isEqualTo("file_rename");
        assertThat(rename.entities()).containsEntry("target_name", "报告一.pdf");
        assertThat(rename.missingSlots()).contains("new_name");
        assertThat(rename.interaction().stage()).isEqualTo("NEED_CLARIFICATION");
        assertThat(rename.assistantText()).contains("新名称");

        assertThat(renamed.intentId()).isEqualTo("file_rename");
        assertThat(renamed.entities())
                .containsEntry("target_name", "报告一.pdf")
                .containsEntry("new_name", "最终报告.pdf");
        assertThat(renamed.candidateBinding().selectedCandidate().nodeId()).isEqualTo(811L);
        assertThat(renamed.interaction().stage()).isEqualTo("NEED_CONFIRMATION");
        assertThat(requests).hasSize(2);
    }

    @Test
    void genericOpenUsesUniquePreviousCandidateInsteadOfSearchingForGenericNoun() {
        CandidateSearchPort port = request -> new CandidateBindingResult(
                "search_results_ready",
                "test",
                request.query(),
                "FOLDER",
                List.of(new CandidateItem(821L, null, "测试目录", "FOLDER", 0L, "", "", "")),
                "找到测试目录。"
        );
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse found = service.plan(
                new AssistantPlanRequest("找到测试目录这个文件夹", ""),
                "Bearer token"
        );
        IntentRecognitionResponse opened = service.plan(
                new AssistantPlanRequest("打开文件夹", found.conversation().conversationId()),
                "Bearer token"
        );

        assertThat(opened.semanticFrame().relation()).isEqualTo("FOLLOW_UP");
        assertThat(opened.semanticFrame().operation()).isEqualTo("NAVIGATE");
        assertThat(opened.semanticFrame().scope().type()).isEqualTo("PREVIOUS_RESULTS");
        assertThat(opened.candidateBinding().selectedCandidate().nodeId()).isEqualTo(821L);
    }

    @Test
    void namedNavigationUsesDeterministicStructureAndKeepsListingSeparate() {
        AtomicInteger modelCalls = new AtomicInteger();
        IntentRecognitionService countingRecognitionService = new IntentRecognitionService(
                request -> {
                    modelCalls.incrementAndGet();
                    return Optional.empty();
                },
                intentRouter,
                configLoader
        );
        List<CandidateSearchRequest> requests = new java.util.ArrayList<>();
        CandidateSearchPort port = request -> {
            requests.add(request);
            CandidateItem candidate = "FILE".equals(request.candidateType())
                    ? new CandidateItem(832L, 1L, "合同.pdf", "FILE", 1024L, "pdf", "application/pdf", "")
                    : new CandidateItem(831L, null, "测试目录", "FOLDER", 0L, "", "", "");
            return new CandidateBindingResult(
                    "single_candidate",
                    "test",
                    request.query(),
                    request.candidateType(),
                    List.of(candidate),
                    "已定位候选。"
            );
        };
        AssistantConversationService service = conversationServiceWith(
                port,
                request -> CollectionPreviewResult.skipped("not_requested", "not requested"),
                countingRecognitionService
        );

        IntentRecognitionResponse folder = service.plan(
                new AssistantPlanRequest("打开测试目录", ""),
                "Bearer token"
        );
        IntentRecognitionResponse file = service.plan(
                new AssistantPlanRequest("请帮我打开合同.pdf", ""),
                "Bearer token"
        );
        IntentRecognitionResponse contents = service.plan(
                new AssistantPlanRequest("打开测试目录，里面的文件列出来", ""),
                "Bearer token"
        );
        IntentRecognitionResponse entered = service.plan(
                new AssistantPlanRequest("进入到测试目录里面", ""),
                "Bearer token"
        );

        assertThat(modelCalls).hasValue(0);
        assertThat(folder.semanticFrame().operation()).isEqualTo("NAVIGATE");
        assertThat(folder.semanticFrame().query().mode()).isEqualTo("NAME_EXACT");
        assertThat(folder.semanticFrame().query().resultType()).isEqualTo("FOLDER");
        assertThat(folder.entities()).containsEntry("target_name", "测试目录");

        assertThat(file.semanticFrame().operation()).isEqualTo("OPEN_FILE");
        assertThat(file.semanticFrame().query().resultType()).isEqualTo("FILE");
        assertThat(file.entities()).containsEntry("target_name", "合同.pdf");

        assertThat(contents.semanticFrame().operation()).isEqualTo("SEARCH");
        assertThat(contents.semanticFrame().query().mode()).isEqualTo("LIST_CHILDREN");
        assertThat(contents.semanticFrame().query().resultType()).isEqualTo("FILE");
        assertThat(contents.semanticFrame().scope().type()).isEqualTo("NAMED_FOLDER");
        assertThat(contents.entities()).containsEntry("target_folder", "测试目录");

        assertThat(entered.semanticFrame().operation()).isEqualTo("NAVIGATE");
        assertThat(entered.semanticFrame().query().mode()).isEqualTo("NAME_EXACT");
        assertThat(entered.entities()).containsEntry("target_name", "测试目录");

        assertThat(requests).hasSize(4);
        assertThat(requests.get(0).queryMode()).isEqualTo("name_search");
        assertThat(requests.get(0).candidateType()).isEqualTo("FOLDER");
        assertThat(requests.get(0).queryRole()).isEqualTo("target_folder");
        assertThat(requests.get(0).query()).isEqualTo("测试目录");
        assertThat(requests.get(1).candidateType()).isEqualTo("FILE");
        assertThat(requests.get(1).queryRole()).isEqualTo("target_name");
        assertThat(requests.get(1).query()).isEqualTo("合同.pdf");
        assertThat(requests.get(2).queryMode()).isEqualTo("directory_list");
        assertThat(requests.get(2).scope()).isEqualTo("named_folder");
        assertThat(requests.get(2).targetFolder()).isEqualTo("测试目录");
    }

    @Test
    void genericNavigationWithoutContextAsksForConcreteTarget() {
        AtomicInteger searches = new AtomicInteger();
        AssistantConversationService service = conversationServiceWith(request -> {
            searches.incrementAndGet();
            return CandidateBindingResult.skipped("unexpected", "unexpected");
        });

        IntentRecognitionResponse response = service.plan(
                new AssistantPlanRequest("打开文件夹", ""),
                "Bearer token"
        );

        assertThat(searches).hasValue(0);
        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.semanticFrame().ambiguities()).contains("missing_navigation_target");
        assertThat(response.assistantText()).contains("具体是哪个文件或文件夹");
    }

    @Test
    void namedLocationQuestionSearchesExplicitFileInsteadOfUsingContextIntent() {
        CandidateSearchRequest[] captured = new CandidateSearchRequest[1];
        AssistantConversationService service = conversationServiceWith(request -> {
            captured[0] = request;
            return new CandidateBindingResult(
                    "search_results_ready",
                    "test",
                    request.query(),
                    request.candidateType(),
                    List.of(new CandidateItem(
                            833L,
                            70L,
                            "测试图片",
                            "FILE",
                            1024L,
                            "",
                            "image/png",
                            "",
                            "/文件记录/测试图片",
                            List.of()
                    )),
                    "已定位候选。"
            );
        });

        IntentRecognitionResponse response = service.plan(
                new AssistantPlanRequest("测试图片这个文件在哪", ""),
                "Bearer token"
        );

        assertThat(response.intentId()).isEqualTo("file_search");
        assertThat(response.semanticFrame().operation()).isEqualTo("SEARCH");
        assertThat(response.semanticFrame().relation()).isEqualTo("NEW_TASK");
        assertThat(response.semanticFrame().query().resultType()).isEqualTo("FILE");
        assertThat(captured[0].queryRole()).isEqualTo("target_name");
        assertThat(captured[0].query()).isEqualTo("测试图片这个文件");
        assertThat(response.candidateBinding().candidates())
                .extracting(CandidateItem::name)
                .containsExactly("测试图片");
    }

    @Test
    void genericLocationQuestionWithoutContextAsksForConcreteTarget() {
        AtomicInteger searches = new AtomicInteger();
        AssistantConversationService service = conversationServiceWith(request -> {
            searches.incrementAndGet();
            return CandidateBindingResult.skipped("unexpected", "unexpected");
        });

        IntentRecognitionResponse response = service.plan(
                new AssistantPlanRequest("这个文件在哪", ""),
                "Bearer token"
        );

        assertThat(searches).hasValue(0);
        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.assistantText()).contains("具体是哪个文件或文件夹");
    }

    @Test
    void folderMutationUsesCanonicalSemanticTargetForCandidateBinding() {
        CandidateSearchRequest[] captured = new CandidateSearchRequest[1];
        CandidateSearchPort port = request -> {
            captured[0] = request;
            return new CandidateBindingResult(
                    "single_candidate",
                    "test",
                    request.query(),
                    request.candidateType(),
                    List.of(new CandidateItem(841L, null, "测试目录", "FOLDER", 0L, "", "", "")),
                    "已定位候选。"
            );
        };
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse response = service.plan(
                new AssistantPlanRequest("删除测试目录这个文件夹", ""),
                "Bearer token"
        );

        assertThat(response.intentId()).isEqualTo("file_delete");
        assertThat(response.entities())
                .containsEntry("target_name", "测试目录")
                .containsEntry("result_type", "FOLDER");
        assertThat(captured[0].query()).isEqualTo("测试目录");
        assertThat(captured[0].candidateType()).isEqualTo("FOLDER");
        assertThat(response.candidateBinding().candidates()).singleElement().satisfies(candidate ->
                assertThat(candidate.nodeId()).isEqualTo(841L)
        );
        assertThat(response.interaction().stage()).isEqualTo("NEED_CONFIRMATION");
    }

    @Test
    void naturalConfirmationAliasBuildsBackendDraft() {
        CandidateSearchPort port = request -> new CandidateBindingResult(
                "single_candidate",
                "test",
                request.query(),
                "FOLDER",
                List.of(new CandidateItem(842L, null, "测试目录", "FOLDER", 0L, "", "", "")),
                "已定位候选。"
        );
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse planned = service.plan(
                new AssistantPlanRequest("删除测试目录这个文件夹", ""),
                "Bearer token"
        );
        IntentRecognitionResponse confirmed = service.plan(
                new AssistantPlanRequest("确认计划。", planned.conversation().conversationId()),
                "Bearer token"
        );

        assertThat(confirmed.nextAction()).isEqualTo("handoff_to_backend");
        assertThat(confirmed.backendActionDraft().status()).isEqualTo("backend_action_ready");
        assertThat(confirmed.backendActionDraft().actionType()).isEqualTo("delete");
        assertThat(confirmed.backendActionDraft().path()).isEqualTo("/api/storage/nodes/842");
    }

    @Test
    void deleteAllFilesInThisFolderUsesCommonParentFromPreviousListing() {
        List<CandidateItem> listedFiles = List.of(
                new CandidateItem(851L, 850L, "报告一.pdf", "FILE", 100L, "pdf", "application/pdf", "", "/测试目录/报告一.pdf", List.of()),
                new CandidateItem(852L, 850L, "报告二.docx", "FILE", 200L, "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "", "/测试目录/报告二.docx", List.of())
        );
        AtomicInteger searches = new AtomicInteger();
        CandidateSearchPort port = request -> {
            searches.incrementAndGet();
            return new CandidateBindingResult(
                    "search_results_ready",
                    "test",
                    request.query(),
                    "FILE",
                    listedFiles,
                    "已列出测试目录文件。"
            );
        };
        CollectionPreviewPort previewPort = request -> new CollectionPreviewResult(
                "preview_ready",
                "test",
                request.filter(),
                listedFiles,
                listedFiles.size(),
                true,
                "已生成完整预览。"
        );
        AssistantConversationService service = conversationServiceWith(port, previewPort);
        AssistantClientContext clientContext = new AssistantClientContext(
                null,
                "/",
                Map.of(),
                "mobile_contract_v1",
                List.of("collection.trash", "collection.trash_scoped")
        );

        IntentRecognitionResponse listed = service.plan(
                new AssistantPlanRequest("列出测试目录中的文件", "", clientContext),
                "Bearer token"
        );
        IntentRecognitionResponse deletion = service.plan(
                new AssistantPlanRequest(
                        "删除这个文件夹中的所有文件",
                        listed.conversation().conversationId(),
                        clientContext
                ),
                "Bearer token"
        );

        assertThat(searches).hasValue(1);
        assertThat(deletion.actionDraft().type()).isEqualTo("collection.trash_scoped");
        assertThat(deletion.entities())
                .containsEntry("source_kind", CollectionOperationSelectorResolver.SOURCE_CONTEXT_FOLDER)
                .containsEntry("source_parent_id", 850L);
        assertThat(deletion.actionPlan().status()).isEqualTo("collection_review_required");
        assertThat(deletion.candidateBinding().candidates()).extracting(CandidateItem::nodeId)
                .containsExactly(851L, 852L);
        assertThat(deletion.interaction().stage()).isEqualTo("NEED_CONFIRMATION");
    }

    @Test
    void ordinalMutationWithoutCandidateContextAsksForAReferent() {
        IntentRecognitionResponse response = conversationService.plan(
                new AssistantPlanRequest("删除第一个文件", ""),
                "Bearer token"
        );

        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.interaction().stage()).isEqualTo("NEED_CLARIFICATION");
        assertThat(response.assistantText()).contains("还没有可以承接的文件上下文");
    }

    @Test
    void failedNewLookupInvalidatesPreviousOrdinalContext() {
        AtomicInteger calls = new AtomicInteger();
        CandidateSearchPort port = request -> {
            if (calls.incrementAndGet() == 1) {
                return new CandidateBindingResult(
                        "search_results_ready",
                        "test",
                        request.query(),
                        "FILE",
                        List.of(
                                new CandidateItem(811L, null, "旧列表一.txt", "FILE", 10L, "txt", "text/plain", ""),
                                new CandidateItem(812L, null, "旧列表二.txt", "FILE", 20L, "txt", "text/plain", "")
                        ),
                        "已找到旧列表。"
                );
            }
            return new CandidateBindingResult(
                    "no_candidates",
                    "test",
                    request.query(),
                    request.candidateType(),
                    List.of(),
                    "本轮没有匹配结果。"
            );
        };
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse listed = service.plan(
                new AssistantPlanRequest("查找旧列表文件", ""),
                "Bearer token"
        );
        IntentRecognitionResponse failed = service.plan(
                new AssistantPlanRequest("删除不存在的文件", listed.conversation().conversationId()),
                "Bearer token"
        );
        IntentRecognitionResponse ordinal = service.plan(
                new AssistantPlanRequest("删除第一个文件", failed.conversation().conversationId()),
                "Bearer token"
        );

        assertThat(failed.candidateBinding().status()).isEqualTo("no_candidates");
        assertThat(ordinal.nextAction()).isEqualTo("ask_clarification");
        assertThat(ordinal.interaction().stage()).isEqualTo("NEED_CLARIFICATION");
        assertThat(ordinal.candidateBinding().selectedCandidate()).isNull();
        assertThat(ordinal.entities()).doesNotContainValue("旧列表一.txt");
        assertThat(ordinal.assistantText()).contains("还没有可以承接的文件上下文");
    }

    @Test
    void rootUploadUsesNullParentWithoutStorageSearch() {
        CandidateSearchPort unexpectedSearch = request -> {
            throw new AssertionError("Root upload must not query a physical folder candidate.");
        };
        AssistantConversationService service = conversationServiceWith(unexpectedSearch);

        IntentRecognitionResponse firstTurn = service.plan(
                new AssistantPlanRequest("将这些文件上传到根目录", ""),
                "Bearer token"
        );
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(firstTurn.intentId()).isEqualTo("file_upload");
        assertThat(firstTurn.entities()).containsEntry("target_folder", "根目录");
        assertThat(firstTurn.candidateBinding().source()).isEqualTo("virtual:cloud-drive-root");
        assertThat(firstTurn.actionPlan().status()).isEqualTo("review_required");
        assertThat(firstTurn.actionPlan().steps()).singleElement().satisfies(step ->
                assertThat(step.params()).containsEntry("parentId", null)
        );
        assertThat(secondTurn.nextAction()).isEqualTo("handoff_to_client_upload");
        assertThat(secondTurn.backendActionDraft().status()).isEqualTo("client_action_required");
        assertThat(secondTurn.backendActionDraft().queryParameters()).containsEntry("parentId", null);
        assertThat(secondTurn.backendActionDraft().requiredClientFields()).containsExactly("files");
    }

    @Test
    void selectedLocalItemsSatisfyUploadClientInputWithoutExposingLocalPaths() {
        SingleCandidateSearchPort port = new SingleCandidateSearchPort(501L, "项目资料");
        AssistantConversationService service = conversationServiceWith(port);
        AssistantClientContext clientContext = new AssistantClientContext(
                null,
                "根目录",
                Map.of("files", 3, "folders", 1)
        );

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "把这些文件上传到项目资料",
                "",
                clientContext
        ), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId(),
                clientContext
        ), "Bearer token");

        assertThat(firstTurn.actionPlan().status()).isEqualTo("review_required");
        assertThat(firstTurn.actionPlan().requiredClientFields()).isEmpty();
        assertThat(firstTurn.actionPlan().steps()).singleElement().satisfies(step -> {
            assertThat(step.requiredClientFields()).isEmpty();
            assertThat(step.params()).containsEntry("parentId", 501L);
        });
        assertThat(secondTurn.backendActionDraft().requiredClientFields()).isEmpty();
        assertThat(secondTurn.actionPlan().requiredClientFields()).isEmpty();
        assertThat(clientContext.availableClientInputs()).containsOnlyKeys("files", "folders");
    }

    @Test
    void createFolderThenUploadBuildsCompositeActionPlan() {
        SingleCandidateSearchPort port = new SingleCandidateSearchPort(901L, "项目资料");
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "在项目资料下新建名为归档的文件夹，然后上传文件",
                ""
        ), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(firstTurn.intentId()).isEqualTo("folder_create_then_upload");
        assertThat(firstTurn.entities()).containsEntry("target_folder", "项目资料");
        assertThat(firstTurn.entities()).containsEntry("new_folder_name", "归档");
        assertThat(firstTurn.actionPlan().planKind()).isEqualTo("composite");
        assertThat(firstTurn.actionPlan().actionType()).isEqualTo("composite.create_folder_then_upload");
        assertThat(firstTurn.actionPlan().status()).isEqualTo("review_required");
        assertThat(firstTurn.actionPlan().steps()).extracting(ActionPlanStep::action)
                .containsExactly("folder.create", "file.upload");
        assertThat(firstTurn.actionPlan().steps().getFirst().params())
                .containsEntry("parentId", 901L)
                .containsEntry("folderName", "归档");

        assertThat(secondTurn.backendActionDraft().status()).isEqualTo("unsupported_action");
        assertThat(secondTurn.actionPlan().status()).isEqualTo("client_input_required");
        assertThat(secondTurn.actionPlan().requiredClientFields()).containsExactly("files");
        assertThat(secondTurn.actionPlan().steps().get(0).status()).isEqualTo("ready");
        assertThat(secondTurn.actionPlan().steps().get(1).status()).isEqualTo("blocked");
    }

    @Test
    void nameContainsDeleteBuildsCollectionActionPlanWithoutSingleCandidateBinding() {
        PreviewPort previewPort = new PreviewPort(List.of(
                new CandidateItem(801L, null, "测试-1.txt", "FILE", 12L, "txt", "text/plain", ""),
                new CandidateItem(802L, null, "测试-2.txt", "FILE", 34L, "txt", "text/plain", "")
        ), 2, true);
        AssistantConversationService service = conversationServiceWith(
                request -> {
                    throw new AssertionError("collection delete should not call single-candidate search");
                },
                previewPort
        );

        IntentRecognitionResponse response = service.plan(new AssistantPlanRequest(
                "删除名称带测试的文件",
                ""
        ), "Bearer token");

        ActionPlan plan = response.actionPlan();
        ActionPlanBinding sourceCollection = plan.bindings().get("sourceCollection");

        assertThat(response.intentId()).isEqualTo("collection_delete_by_name");
        assertThat(response.candidateBinding().status()).isEqualTo("collection_filter_only");
        assertThat(response.conversation().status()).isEqualTo("collection_review_required");
        assertThat(plan.planKind()).isEqualTo("collection");
        assertThat(plan.actionType()).isEqualTo("collection.trash_by_name_contains");
        assertThat(plan.status()).isEqualTo("collection_review_required");
        assertThat(response.semanticFrame().query().mode()).isEqualTo("NAME_CONTAINS");
        assertThat(response.semanticFrame().query().resultType()).isEqualTo("FILE");
        assertThat(previewPort.lastRequest.filter())
                .containsEntry("nameContains", "测试")
                .containsEntry("nodeType", "FILE")
                .containsEntry("includeFolders", false);
        assertThat(sourceCollection.status()).isEqualTo("resolved");
        assertThat(sourceCollection.candidates()).hasSize(2);
        assertThat(sourceCollection.count()).isEqualTo(2);
        assertThat(sourceCollection.filter()).containsEntry("nameContains", "测试");
        assertThat(sourceCollection.filter()).containsEntry("nodeType", "FILE");
        assertThat(sourceCollection.filter()).containsEntry("includeFolders", false);
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().getFirst().action()).isEqualTo("node.batch_trash");
        assertThat(plan.steps().getFirst().params())
                .containsEntry("nodeIds", "$bindings.sourceCollection.nodeIds");
    }

    @Test
    void nameContainsDeleteKeepsFoldersOnlyWhenUserIncludesThem() {
        PreviewPort previewPort = new PreviewPort(List.of(
                new CandidateItem(801L, null, "测试目录", "FOLDER", 0L, "", "", ""),
                new CandidateItem(802L, null, "测试文件.txt", "FILE", 34L, "txt", "text/plain", "")
        ), 2, true);
        AssistantConversationService service = conversationServiceWith(
                request -> {
                    throw new AssertionError("collection delete should not call single-candidate search");
                },
                previewPort
        );

        IntentRecognitionResponse response = service.plan(new AssistantPlanRequest(
                "删除名称带测试的文件或文件夹",
                ""
        ), "Bearer token");

        assertThat(response.semanticFrame().query().resultType()).isEqualTo("ANY");
        assertThat(previewPort.lastRequest.filter())
                .containsEntry("nodeType", "ANY")
                .containsEntry("includeFolders", true);
    }

    @Test
    void confirmCollectionDeleteBuildsBatchTrashDraft() {
        PreviewPort previewPort = new PreviewPort(List.of(
                new CandidateItem(801L, null, "测试-1.txt", "FILE", 12L, "txt", "text/plain", ""),
                new CandidateItem(802L, null, "测试-2.txt", "FILE", 34L, "txt", "text/plain", "")
        ), 2, true);
        AssistantConversationService service = conversationServiceWith(
                request -> {
                    throw new AssertionError("collection delete should not call single-candidate search");
                },
                previewPort
        );

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "把名字带有测试的文件全部删除",
                ""
        ), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(secondTurn.nextAction()).isEqualTo("handoff_to_backend");
        assertThat(secondTurn.conversation().status()).isEqualTo("backend_action_ready");
        assertThat(secondTurn.backendActionDraft().status()).isEqualTo("backend_action_ready");
        assertThat(secondTurn.backendActionDraft().actionType()).isEqualTo("collection.trash_by_name_contains");
        assertThat(secondTurn.backendActionDraft().method()).isEqualTo("POST");
        assertThat(secondTurn.backendActionDraft().path()).isEqualTo("/api/storage/nodes/batch/trash");
        assertThat(secondTurn.backendActionDraft().body()).containsEntry("nodeIds", List.of(801L, 802L));
        assertThat(secondTurn.backendActionDraft().targetCandidate()).isNull();
        assertThat(secondTurn.actionPlan().status()).isEqualTo("ready_to_execute");
        assertThat(secondTurn.actionPlan().steps().getFirst().status()).isEqualTo("ready");
        assertThat(previewPort.calls).isEqualTo(1);
    }

    @Test
    void legacyCollectionConfirmationUsesFullReviewedSnapshotWithoutRescanning() {
        List<CandidateItem> candidates = java.util.stream.LongStream.rangeClosed(1, 25)
                .mapToObj(id -> new CandidateItem(
                        800L + id,
                        null,
                        "测试-" + id + ".txt",
                        "FILE",
                        10L,
                        "txt",
                        "text/plain",
                        ""
                ))
                .toList();
        PreviewPort previewPort = new PreviewPort(candidates, candidates.size(), true);
        AssistantConversationService service = conversationServiceWith(
                request -> {
                    throw new AssertionError("collection delete should not call single-candidate search");
                },
                previewPort
        );

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "把名字带有测试的文件全部删除",
                ""
        ), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(firstTurn.actionPlan().bindings().get("sourceCollection").candidates()).hasSize(20);
        assertThat(firstTurn.actionPlan().bindings().get("sourceCollection").count()).isEqualTo(25);
        assertThat(secondTurn.backendActionDraft().body().get("nodeIds"))
                .asList()
                .hasSize(25);
        assertThat(previewPort.calls).isEqualTo(1);
    }

    @Test
    void extensionMoveBuildsCollectionMovePlanWithTargetFolderBinding() {
        SingleCandidateSearchPort port = new SingleCandidateSearchPort(902L, "归档");
        PreviewPort previewPort = new PreviewPort(List.of(
                new CandidateItem(811L, null, "方案.pdf", "FILE", 120L, "pdf", "application/pdf", "")
        ), 1, true);
        AssistantConversationService service = conversationServiceWith(port, previewPort);

        IntentRecognitionResponse response = service.plan(new AssistantPlanRequest(
                "把后缀 pdf 的文件全部移动到归档文件夹",
                ""
        ), "Bearer token");

        ActionPlan plan = response.actionPlan();
        ActionPlanBinding sourceCollection = plan.bindings().get("sourceCollection");
        ActionPlanBinding targetParent = plan.bindings().get("targetParent");

        assertThat(response.intentId()).isEqualTo("collection_move_by_extension");
        assertThat(port.lastRequest.candidateType()).isEqualTo("FOLDER");
        assertThat(port.lastRequest.query()).isEqualTo("归档文件夹");
        assertThat(plan.planKind()).isEqualTo("collection");
        assertThat(plan.actionType()).isEqualTo("collection.move_by_extension");
        assertThat(plan.status()).isEqualTo("collection_review_required");
        assertThat(response.conversation().status()).isEqualTo("collection_review_required");
        assertThat(previewPort.lastRequest.filter()).containsEntry("extension", "PDF");
        assertThat(sourceCollection.filter()).containsEntry("extension", "PDF");
        assertThat(sourceCollection.status()).isEqualTo("resolved");
        assertThat(sourceCollection.candidates()).hasSize(1);
        assertThat(sourceCollection.count()).isEqualTo(1);
        assertThat(targetParent.selectedCandidate().nodeId()).isEqualTo(902L);
        assertThat(plan.steps().getFirst().action()).isEqualTo("node.batch_move");
        assertThat(plan.steps().getFirst().params())
                .containsEntry("nodeIds", "$bindings.sourceCollection.nodeIds")
                .containsEntry("parentId", 902L);
    }

    @Test
    void directExtensionMovePhraseBuildsCollectionMovePlan() {
        SingleCandidateSearchPort port = new SingleCandidateSearchPort(903L, "资料");
        PreviewPort previewPort = new PreviewPort(List.of(
                new CandidateItem(812L, null, "合同.pdf", "FILE", 120L, "pdf", "application/pdf", "")
        ), 1, true);
        AssistantConversationService service = conversationServiceWith(port, previewPort);

        IntentRecognitionResponse response = service.plan(new AssistantPlanRequest(
                "把 pdf 文件移动到资料文件夹",
                ""
        ), "Bearer token");

        ActionPlan plan = response.actionPlan();
        ActionPlanBinding sourceCollection = plan.bindings().get("sourceCollection");
        ActionPlanBinding targetParent = plan.bindings().get("targetParent");

        assertThat(response.intentId()).isEqualTo("collection_move_by_extension");
        assertThat(response.entities()).containsEntry("extension", "PDF");
        assertThat(response.entities()).containsEntry("target_folder", "资料文件夹");
        assertThat(port.lastRequest.candidateType()).isEqualTo("FOLDER");
        assertThat(port.lastRequest.query()).isEqualTo("资料文件夹");
        assertThat(plan.actionType()).isEqualTo("collection.move_by_extension");
        assertThat(plan.status()).isEqualTo("collection_review_required");
        assertThat(previewPort.lastRequest.filter()).containsEntry("extension", "PDF");
        assertThat(sourceCollection.candidates()).hasSize(1);
        assertThat(targetParent.selectedCandidate().nodeId()).isEqualTo(903L);
    }

    @Test
    void confirmCollectionMoveBuildsBatchMoveDraft() {
        SingleCandidateSearchPort port = new SingleCandidateSearchPort(902L, "归档");
        PreviewPort previewPort = new PreviewPort(List.of(
                new CandidateItem(811L, null, "方案.pdf", "FILE", 120L, "pdf", "application/pdf", "")
        ), 1, true);
        AssistantConversationService service = conversationServiceWith(port, previewPort);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "把后缀 pdf 的文件全部移动到归档文件夹",
                ""
        ), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(secondTurn.nextAction()).isEqualTo("handoff_to_backend");
        assertThat(secondTurn.conversation().status()).isEqualTo("backend_action_ready");
        assertThat(secondTurn.backendActionDraft().status()).isEqualTo("backend_action_ready");
        assertThat(secondTurn.backendActionDraft().actionType()).isEqualTo("collection.move_by_extension");
        assertThat(secondTurn.backendActionDraft().method()).isEqualTo("PUT");
        assertThat(secondTurn.backendActionDraft().path()).isEqualTo("/api/storage/nodes/batch/move");
        assertThat(secondTurn.backendActionDraft().body())
                .containsEntry("nodeIds", List.of(811L))
                .containsEntry("parentId", 902L);
        assertThat(secondTurn.backendActionDraft().targetCandidate().nodeId()).isEqualTo(902L);
        assertThat(secondTurn.actionPlan().status()).isEqualTo("ready_to_execute");
        assertThat(secondTurn.actionPlan().steps().getFirst().status()).isEqualTo("ready");
        assertThat(previewPort.calls).isEqualTo(1);
    }

    @Test
    void categoryMoveBuildsExecutableBatchMoveDraft() {
        SingleCandidateSearchPort port = new SingleCandidateSearchPort(920L, "图片目录");
        PreviewPort previewPort = new PreviewPort(List.of(
                new CandidateItem(921L, null, "照片.jpg", "FILE", 120L, "jpg", "image/jpeg", ""),
                new CandidateItem(922L, null, "截图.png", "FILE", 80L, "png", "image/png", "")
        ), 2, true);
        AssistantConversationService service = conversationServiceWith(port, previewPort);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "把所有图片移动到图片目录",
                ""
        ), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(firstTurn.intentId()).isEqualTo("collection_move_by_category");
        assertThat(firstTurn.actionPlan().actionType()).isEqualTo("collection.move_by_category");
        assertThat(firstTurn.actionPlan().bindings().get("sourceCollection").filter())
                .containsEntry("category", "图片");
        assertThat(secondTurn.backendActionDraft().status()).isEqualTo("backend_action_ready");
        assertThat(secondTurn.backendActionDraft().body())
                .containsEntry("nodeIds", List.of(921L, 922L))
                .containsEntry("parentId", 920L);
    }

    @Test
    void batchRenameConfirmationContainsCompleteOldToNewMapping() {
        PreviewPort previewPort = new PreviewPort(List.of(
                new CandidateItem(931L, null, "合同.pdf", "FILE", 120L, "pdf", "application/pdf", ""),
                new CandidateItem(932L, null, "合同附件", "FOLDER", 0L, "", "", "")
        ), 2, true);
        AssistantConversationService service = conversationServiceWith(
                request -> {
                    throw new AssertionError("batch rename should not use single candidate binding");
                },
                previewPort
        );

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "将名称带有合同的文件或文件夹统一在头部加上归档-",
                ""
        ), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(firstTurn.intentId()).isEqualTo("collection_rename_add_prefix");
        assertThat(firstTurn.actionPlan().bindings().get("sourceCollection").candidates()).hasSize(2);
        assertThat(secondTurn.backendActionDraft().status()).isEqualTo("backend_action_ready");
        assertThat(secondTurn.backendActionDraft().path()).isEqualTo("/api/storage/nodes/batch/rename");
        assertThat(secondTurn.backendActionDraft().body().get("items")).isEqualTo(List.of(
                Map.of("nodeId", 931L, "name", "归档-合同.pdf"),
                Map.of("nodeId", 932L, "name", "归档-合同附件")
        ));
    }

    @Test
    void incompleteCollectionPreviewBlocksCollectionReview() {
        PreviewPort previewPort = new PreviewPort(List.of(
                new CandidateItem(821L, null, "测试-扫描内.txt", "FILE", 12L, "txt", "text/plain", "")
        ), 50, false);
        AssistantConversationService service = conversationServiceWith(
                request -> {
                    throw new AssertionError("collection delete should not call single-candidate search");
                },
                previewPort
        );

        IntentRecognitionResponse response = service.plan(new AssistantPlanRequest(
                "把名字带有测试的文件全部删除",
                ""
        ), "Bearer token");

        ActionPlanBinding sourceCollection = response.actionPlan().bindings().get("sourceCollection");

        assertThat(response.actionPlan().status()).isEqualTo("binding_required");
        assertThat(response.conversation().status()).isEqualTo("waiting_for_collection_preview");
        assertThat(sourceCollection.status()).isEqualTo("unresolved");
        assertThat(sourceCollection.candidates()).hasSize(1);
        assertThat(sourceCollection.count()).isEqualTo(50);
        assertThat(response.actionPlan().messages()).extracting(ActionPlanMessage::code)
                .contains("preview_incomplete");
    }

    @Test
    void confirmedActionWithoutCandidateNodeIdDoesNotBuildBackendDraft() {
        AssistantConversationService service = conversationServiceWith(request -> new CandidateBindingResult(
                "single_candidate",
                "test",
                request.query(),
                request.candidateType(),
                List.of(new CandidateItem(null, null, "临时截图.png", "FILE", 0L, "png", "image/png", "")),
                "已匹配到 1 个候选，等待用户确认。"
        ));

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest("删除临时截图", ""), "Bearer token");
        IntentRecognitionResponse secondTurn = service.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId()
        ), "Bearer token");

        assertThat(secondTurn.nextAction()).isEqualTo("wait_for_user_confirmation");
        assertThat(secondTurn.conversation().status()).isEqualTo("waiting_for_user_confirmation");
        assertThat(secondTurn.backendActionDraft().status()).isEqualTo("missing_candidate_fields");
        assertThat(secondTurn.backendActionDraft().path()).isBlank();
    }

    @Test
    void scopedFolderChildrenMoveBindsSourceCollectionAndDestinationIndependently() {
        CandidateSearchPort folderPort = request -> {
            long id = "sourceFolder".equals(request.queryRole()) ? 801L : 902L;
            String name = "sourceFolder".equals(request.queryRole()) ? "测试目录" : "文件记录";
            String path = "/" + name;
            CandidateItem folder = new CandidateItem(id, null, name, "FOLDER", 0L, "", "", "")
                    .withPath(path, List.of());
            return new CandidateBindingResult(
                    "single_candidate",
                    "test",
                    request.query(),
                    "FOLDER",
                    List.of(folder),
                    "已唯一定位目录。"
            );
        };
        PreviewPort previewPort = new PreviewPort(List.of(
                new CandidateItem(811L, 801L, "报告一.pdf", "FILE", 10L, "pdf", "application/pdf", ""),
                new CandidateItem(812L, 801L, "报告二.docx", "FILE", 20L, "docx", "application/docx", "")
        ), 2, true);
        AssistantConversationService service = conversationServiceWith(folderPort, previewPort);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "将测试目录中的所有文件移动到文件记录",
                "",
                scopedClientContext()
        ), "Bearer scoped-user");
        IntentRecognitionResponse confirmed = service.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId()
        ), "Bearer scoped-user");

        assertThat(firstTurn.actionDraft().type()).isEqualTo("collection.move");
        assertThat(firstTurn.entities())
                .containsEntry("selector_version", "source_selector_v2")
                .containsEntry("source_folder", "测试目录")
                .containsEntry("source_node_type", "FILE")
                .containsEntry("target_folder", "文件记录");
        assertThat(previewPort.lastRequest.filter())
                .containsEntry("parentId", 801L)
                .containsEntry("nodeType", "FILE")
                .containsEntry("directChildren", true);
        assertThat(firstTurn.actionPlan().bindings()).containsKeys(
                "sourceFolder",
                "sourceCollection",
                "targetParent"
        );
        assertThat(firstTurn.actionPlan().bindings().get("sourceCollection").candidates()).hasSize(2);
        assertThat(firstTurn.actionPlan().bindings().get("sourceCollection").filter())
                .containsKeys("snapshotId", "snapshotCount");
        assertThat(confirmed.backendActionDraft().status()).isEqualTo("backend_action_ready");
        assertThat(confirmed.backendActionDraft().body())
                .containsEntry("nodeIds", List.of(811L, 812L))
                .containsEntry("parentId", 902L);
        assertThat(previewPort.calls).isEqualTo(1);
    }

    @Test
    void confirmationExecutesReviewedSnapshotWithoutRescanningOrExpandingPayload() {
        CandidateSearchPort folderPort = request -> {
            long id = "sourceFolder".equals(request.queryRole()) ? 801L : 902L;
            String name = "sourceFolder".equals(request.queryRole()) ? "测试目录" : "文件记录";
            return new CandidateBindingResult(
                    "single_candidate",
                    "test",
                    request.query(),
                    "FOLDER",
                    List.of(new CandidateItem(id, null, name, "FOLDER", 0L, "", "", "")
                            .withPath("/" + name, List.of())),
                    "已唯一定位目录。"
            );
        };
        List<CandidateItem> candidates = java.util.stream.LongStream.rangeClosed(1, 25)
                .mapToObj(id -> new CandidateItem(
                        800L + id,
                        801L,
                        "文件" + id + ".txt",
                        "FILE",
                        10L,
                        "txt",
                        "text/plain",
                        ""
                ))
                .toList();
        PreviewPort previewPort = new PreviewPort(candidates, candidates.size(), true);
        AssistantConversationService service = conversationServiceWith(folderPort, previewPort);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "将测试目录中的所有文件移动到文件记录",
                "",
                scopedClientContext()
        ), "Bearer scoped-user");
        IntentRecognitionResponse confirmed = service.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId()
        ), "Bearer scoped-user");

        assertThat(firstTurn.candidateBinding().candidates()).hasSize(20);
        assertThat(firstTurn.actionPlan().bindings().get("sourceCollection").candidates()).hasSize(20);
        assertThat(firstTurn.actionPlan().bindings().get("sourceCollection").count()).isEqualTo(25);
        assertThat(confirmed.backendActionDraft().body().get("nodeIds"))
                .asList()
                .hasSize(25);
        assertThat(previewPort.calls).isEqualTo(1);
    }

    @Test
    void folderChildrenDeleteUsesAllDirectFilesInsteadOfForcingSingleSelection() {
        CandidateSearchPort folderPort = request -> new CandidateBindingResult(
                "single_candidate",
                "test",
                request.query(),
                "FOLDER",
                List.of(new CandidateItem(801L, null, "测试目录", "FOLDER", 0L, "", "", "")
                        .withPath("/测试目录", List.of())),
                "已唯一定位目录。"
        );
        PreviewPort previewPort = new PreviewPort(List.of(
                new CandidateItem(811L, 801L, "文件一.txt", "FILE", 10L, "txt", "text/plain", ""),
                new CandidateItem(812L, 801L, "文件二.txt", "FILE", 20L, "txt", "text/plain", "")
        ), 2, true);
        AssistantConversationService service = conversationServiceWith(folderPort, previewPort);

        IntentRecognitionResponse response = service.plan(new AssistantPlanRequest(
                "删除测试目录里的全部文件",
                "",
                scopedClientContext()
        ), "Bearer scoped-user");

        assertThat(response.actionDraft().type()).isEqualTo("collection.trash_scoped");
        assertThat(response.actionPlan().status()).isEqualTo("collection_review_required");
        assertThat(response.candidateBinding().status()).isEqualTo("search_results_ready");
        assertThat(response.actionPlan().bindings().get("sourceCollection").count()).isEqualTo(2);
        assertThat(response.actionPlan().bindings().get("sourceCollection").filter())
                .containsEntry("parentId", 801L)
                .containsEntry("nodeType", "FILE");
    }

    @Test
    void currentFolderCollectionUsesAuthenticatedClientLocationWithoutCandidateSearch() {
        PreviewPort previewPort = new PreviewPort(List.of(
                new CandidateItem(811L, 850L, "文件一.txt", "FILE", 10L, "txt", "text/plain", "")
        ), 1, true);
        CandidateSearchPort searchPort = request -> {
            throw new AssertionError("当前目录不应退化为名称搜索");
        };
        AssistantConversationService service = conversationServiceWith(searchPort, previewPort);
        AssistantClientContext clientContext = new AssistantClientContext(
                850L,
                "/测试目录",
                Map.of(),
                "action_bridge_v2",
                List.of("collection.trash_scoped")
        );

        IntentRecognitionResponse response = service.plan(new AssistantPlanRequest(
                "删除当前目录下所有文件",
                "",
                clientContext
        ), "Bearer scoped-user");

        assertThat(response.actionDraft().type()).isEqualTo("collection.trash_scoped");
        assertThat(response.entities())
                .containsEntry("source_kind", CollectionOperationSelectorResolver.SOURCE_CURRENT_FOLDER)
                .containsEntry("source_parent_id", 850L);
        assertThat(previewPort.lastRequest.filter()).containsEntry("parentId", 850L);
    }

    @Test
    void rootFileAndFolderUnionUsesScopedSnapshotAndRevalidatedBackendDraft() {
        List<CandidateItem> candidates = List.of(
                new CandidateItem(811L, null, "根文件.txt", "FILE", 10L, "txt", "text/plain", "2026-08-14T08:00:00+08:00"),
                new CandidateItem(812L, null, "资料", "FOLDER", 0L, "", "", "2026-08-14T08:00:00+08:00")
        );
        CollectionPreviewPort previewPort = request -> {
            Map<String, Object> filter = new LinkedHashMap<>(request.filter());
            filter.put("scopeFingerprint", "scope-hash");
            filter.put("impactFingerprint", "impact-hash");
            filter.put("selectedFileCount", 1);
            filter.put("selectedFolderCount", 1);
            filter.put("descendantCount", 3);
            filter.put("impactCount", 5);
            filter.put("expectedImpactCount", 5);
            filter.put("sourceParentId", "");
            filter.put("sourceRoot", true);
            return new CollectionPreviewResult(
                    "preview_ready",
                    "scoped-trash-test",
                    filter,
                    candidates,
                    candidates.size(),
                    true,
                    "已生成完整影响预览。"
            );
        };
        AssistantConversationService service = conversationServiceWith(request -> CandidateBindingResult.skipped("unused", ""), previewPort);

        IntentRecognitionResponse firstTurn = service.plan(new AssistantPlanRequest(
                "删除根目录下所有文件和文件夹",
                "",
                scopedClientContext()
        ), "Bearer scoped-user");
        IntentRecognitionResponse confirmed = service.plan(new AssistantPlanRequest(
                "确认",
                firstTurn.conversation().conversationId(),
                scopedClientContext()
        ), "Bearer scoped-user");

        assertThat(firstTurn.actionDraft().type()).isEqualTo("collection.trash_scoped");
        assertThat(firstTurn.semanticFrame().operation()).isEqualTo("DELETE");
        assertThat(firstTurn.semanticFrame().query().mode()).isEqualTo("COLLECTION");
        assertThat(firstTurn.semanticFrame().scope().type()).isEqualTo("ROOT");
        assertThat(firstTurn.entities())
                .containsEntry("selector_version", "source_selector_v2")
                .containsEntry("source_kind", CollectionOperationSelectorResolver.SOURCE_ROOT)
                .containsEntry("source_node_type", "ANY");
        assertThat(firstTurn.entities().get("source_node_types")).asList().containsExactly("FILE", "FOLDER");
        assertThat(firstTurn.actionPlan().summary()).contains("1 个文件").contains("1 个文件夹");
        assertThat(firstTurn.actionPlan().messages()).extracting(ActionPlanMessage::code)
                .contains("folder_subtree_will_be_trashed");
        assertThat(confirmed.backendActionDraft().status()).isEqualTo("backend_action_ready");
        assertThat(confirmed.backendActionDraft().path()).isEqualTo("/api/storage/nodes/batch/trash/scoped");
        assertThat(confirmed.backendActionDraft().body())
                .containsEntry("nodeIds", List.of(811L, 812L))
                .containsEntry("scopeFingerprint", "scope-hash")
                .containsEntry("impactFingerprint", "impact-hash")
                .containsEntry("expectedImpactCount", 5)
                .containsEntry("root", true);
    }

    @Test
    void scopedRootDeletionFailsClosedForClientWithoutNewActionContract() {
        IntentRecognitionResponse response = conversationService.plan(new AssistantPlanRequest(
                "删除根目录下所有文件和文件夹",
                ""
        ));

        assertThat(response.intentId()).isEqualTo("fallback");
        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.reason()).isEqualTo("capability_boundary:client_action_contract_outdated");
        assertThat(response.backendActionDraft().status()).isEqualTo("clarification_required");
    }

    @Test
    void rootNodeAndUnscopedCollectionDeletionFailClosed() {
        IntentRecognitionResponse rootNode = conversationService.plan(new AssistantPlanRequest("删除根目录", ""));
        IntentRecognitionResponse unscoped = conversationService.plan(new AssistantPlanRequest("删除所有文件和文件夹", ""));

        assertThat(rootNode.nextAction()).isEqualTo("ask_clarification");
        assertThat(rootNode.actionDraft().type()).isEqualTo("none");
        assertThat(rootNode.assistantText()).contains("根目录本身不能删除");
        assertThat(unscoped.nextAction()).isEqualTo("ask_clarification");
        assertThat(unscoped.actionDraft().type()).isEqualTo("none");
        assertThat(unscoped.assistantText()).contains("目录范围");
    }

    @Test
    void scopedCollectionParserAcceptsOmittedConnectorAndColloquialQuantifier() {
        CandidateSearchPort folderPort = request -> {
            long id = "sourceFolder".equals(request.queryRole()) ? 801L : 902L;
            String name = "sourceFolder".equals(request.queryRole()) ? "测试目录" : "文件记录";
            return new CandidateBindingResult(
                    "single_candidate",
                    "test",
                    request.query(),
                    "FOLDER",
                    List.of(new CandidateItem(id, null, name, "FOLDER", 0L, "", "", "")
                            .withPath("/" + name, List.of())),
                    "已唯一定位目录。"
            );
        };
        PreviewPort previewPort = new PreviewPort(List.of(
                new CandidateItem(811L, 801L, "文件一.txt", "FILE", 10L, "txt", "text/plain", ""),
                new CandidateItem(812L, 801L, "文件二.txt", "FILE", 20L, "txt", "text/plain", "")
        ), 2, true);
        AssistantConversationService service = conversationServiceWith(folderPort, previewPort);

        IntentRecognitionResponse response = service.plan(new AssistantPlanRequest(
                "把测试目录所有文件全移动到文件记录吧",
                "",
                scopedClientContext()
        ), "Bearer scoped-user");

        assertThat(response.actionDraft().type()).isEqualTo("collection.move");
        assertThat(response.entities())
                .containsEntry("source_folder", "测试目录")
                .containsEntry("source_node_type", "FILE")
                .containsEntry("target_folder", "文件记录");
        assertThat(response.actionPlan().bindings().get("sourceCollection").count()).isEqualTo(2);
    }

    @Test
    void previousResultSetCanBeDeletedAsACollection() {
        CandidateSearchPort searchPort = request -> new CandidateBindingResult(
                "search_results_ready",
                "test",
                request.query(),
                "FILE",
                List.of(
                        new CandidateItem(811L, 801L, "文件一.txt", "FILE", 10L, "txt", "text/plain", ""),
                        new CandidateItem(812L, 801L, "文件二.txt", "FILE", 20L, "txt", "text/plain", "")
                ),
                "已找到两个文件。"
        );
        AssistantConversationService service = conversationServiceWith(searchPort);
        IntentRecognitionResponse search = service.plan(
                new AssistantPlanRequest("查找测试文件", ""),
                "Bearer scoped-user"
        );

        IntentRecognitionResponse response = service.plan(new AssistantPlanRequest(
                "删除刚才列出的全部文件",
                search.conversation().conversationId(),
                scopedClientContext()
        ), "Bearer scoped-user");

        assertThat(response.actionDraft().type()).isEqualTo("collection.trash");
        assertThat(response.entities()).containsEntry("source_kind", "PREVIOUS_RESULTS");
        assertThat(response.actionPlan().bindings().get("sourceCollection").count()).isEqualTo(2);
        assertThat(response.nextAction()).isEqualTo("wait_for_user_confirmation");
    }

    @Test
    void previousResultPronounCanBeDeletedAsACompleteCollection() {
        CandidateSearchPort searchPort = request -> new CandidateBindingResult(
                "search_results_ready",
                "test",
                request.query(),
                "FILE",
                List.of(
                        new CandidateItem(811L, 801L, "文件一.txt", "FILE", 10L, "txt", "text/plain", ""),
                        new CandidateItem(812L, 801L, "文件二.txt", "FILE", 20L, "txt", "text/plain", "")
                ),
                "已找到两个文件。"
        );
        AssistantConversationService service = conversationServiceWith(searchPort);
        IntentRecognitionResponse search = service.plan(
                new AssistantPlanRequest("查找测试文件", ""),
                "Bearer scoped-user"
        );

        IntentRecognitionResponse response = service.plan(new AssistantPlanRequest(
                "把它们全删了",
                search.conversation().conversationId(),
                scopedClientContext()
        ), "Bearer scoped-user");

        assertThat(response.actionDraft().type()).isEqualTo("collection.trash");
        assertThat(response.entities()).containsEntry("source_kind", "PREVIOUS_RESULTS");
        assertThat(response.actionPlan().bindings().get("sourceCollection").count()).isEqualTo(2);
    }

    @Test
    void previousResultPronounWithoutContextFailsClosed() {
        IntentRecognitionResponse response = conversationService.plan(new AssistantPlanRequest(
                "把它们全删了",
                "",
                scopedClientContext()
        ), "Bearer scoped-user");

        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.backendActionDraft().status()).isNotEqualTo("backend_action_ready");
        assertThat(response.actionPlan().status()).isEqualTo("binding_required");
        assertThat(response.assistantText()).contains("没有可处理").contains("不会执行");
    }

    @Test
    void ambiguousClearAndRecursiveScopesFailClosedWithGuidance() {
        IntentRecognitionResponse clear = conversationService.plan(new AssistantPlanRequest("清空测试目录", ""));
        IntentRecognitionResponse recursive = conversationService.plan(new AssistantPlanRequest(
                "删除测试目录中包含子文件夹的所有文件",
                ""
        ));

        assertThat(clear.nextAction()).isEqualTo("ask_clarification");
        assertThat(clear.actionDraft().type()).isEqualTo("none");
        assertThat(clear.assistantText()).contains("文件").contains("子文件夹");
        assertThat(recursive.nextAction()).isEqualTo("ask_clarification");
        assertThat(recursive.actionDraft().type()).isEqualTo("none");
        assertThat(recursive.assistantText()).contains("当前层").contains("所有子目录");
    }

    @Test
    void unsupportedExclusionConstraintGetsTargetedGuidance() {
        IntentRecognitionResponse response = conversationService.plan(new AssistantPlanRequest(
                "删除测试目录中的所有文件，除了 PDF",
                "",
                scopedClientContext()
        ));

        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.assistantText()).contains("排除条件").contains("除了/排除");
    }

    @Test
    void conversationContextCannotBeReusedAcrossAuthorizationIdentities() {
        SingleCandidateSearchPort port = new SingleCandidateSearchPort(701L, "临时截图.png");
        AssistantConversationService service = conversationServiceWith(port);

        IntentRecognitionResponse firstTurn = service.plan(
                new AssistantPlanRequest("删除临时截图", ""),
                "Bearer user-a"
        );
        IntentRecognitionResponse otherUserTurn = service.plan(
                new AssistantPlanRequest("确认", firstTurn.conversation().conversationId()),
                "Bearer user-b"
        );

        assertThat(otherUserTurn.conversation().conversationId())
                .isNotEqualTo(firstTurn.conversation().conversationId());
        assertThat(otherUserTurn.backendActionDraft().status()).isNotEqualTo("backend_action_ready");
    }

    private static AssistantClientContext scopedClientContext() {
        return new AssistantClientContext(
                null,
                "/",
                Map.of(),
                "action_bridge_v2",
                List.of("collection.move", "collection.trash", "collection.trash_scoped")
        );
    }

    private static class MultipleCandidateSearchPort implements CandidateSearchPort {
        private int calls;

        @Override
        public CandidateBindingResult search(CandidateSearchRequest request) {
            calls++;
            return new CandidateBindingResult(
                    "multiple_candidates",
                    "test",
                    request.query(),
                    request.candidateType(),
                    List.of(
                            new CandidateItem(101L, null, "临时截图-old.png", "FILE", 12L, "png", "image/png", ""),
                            new CandidateItem(102L, null, "临时截图-new.png", "FILE", 34L, "png", "image/png", "")
                    ),
                    "匹配到多个候选，需要用户选择。"
            );
        }
    }

    private static class SearchResultsPort implements CandidateSearchPort {
        private int calls;

        @Override
        public CandidateBindingResult search(CandidateSearchRequest request) {
            calls++;
            return new CandidateBindingResult(
                    "search_results_ready",
                    "test",
                    request.query(),
                    request.candidateType(),
                    List.of(
                            new CandidateItem(201L, null, "合同-2026.docx", "FILE", 56L, "docx", "application/docx", ""),
                            new CandidateItem(202L, null, "合同扫描件.pdf", "FILE", 78L, "pdf", "application/pdf", "")
                    ),
                    "已匹配到 2 个候选，可展示给用户。"
            );
        }
    }

    private static class SingleSearchResultPort implements CandidateSearchPort {
        private final CandidateItem candidate;
        private int calls;

        private SingleSearchResultPort(CandidateItem candidate) {
            this.candidate = candidate;
        }

        @Override
        public CandidateBindingResult search(CandidateSearchRequest request) {
            calls++;
            return new CandidateBindingResult(
                    "search_results_ready",
                    "test",
                    request.query(),
                    request.candidateType(),
                    List.of(candidate),
                    "已匹配到 1 个候选，可展示给用户。"
            );
        }
    }

    private static class NoCandidateSearchPort implements CandidateSearchPort {
        private CandidateSearchRequest lastRequest;

        @Override
        public CandidateBindingResult search(CandidateSearchRequest request) {
            lastRequest = request;
            return new CandidateBindingResult(
                    "no_candidates",
                    "test",
                    request.query(),
                    request.candidateType(),
                    List.of(),
                    "未匹配到候选文件或目录，可调整线索后重新检索。"
            );
        }
    }

    private static class PreviewPort implements CollectionPreviewPort {
        private final List<CandidateItem> candidates;
        private final int totalCount;
        private final boolean exactCount;
        private int calls;
        private CollectionPreviewRequest lastRequest;

        private PreviewPort(List<CandidateItem> candidates, int totalCount, boolean exactCount) {
            this.candidates = candidates;
            this.totalCount = totalCount;
            this.exactCount = exactCount;
        }

        @Override
        public CollectionPreviewResult preview(CollectionPreviewRequest request) {
            calls++;
            this.lastRequest = request;
            String status = totalCount == 0 ? "no_candidates" : exactCount ? "preview_ready" : "preview_incomplete";
            return new CollectionPreviewResult(
                    status,
                    "test",
                    request.filter(),
                    candidates,
                    totalCount,
                    exactCount,
                    "preview"
            );
        }
    }

    private static class SingleCandidateSearchPort implements CandidateSearchPort {
        private final long nodeId;
        private final String name;
        private CandidateSearchRequest lastRequest;

        private SingleCandidateSearchPort(long nodeId, String name) {
            this.nodeId = nodeId;
            this.name = name;
        }

        @Override
        public CandidateBindingResult search(CandidateSearchRequest request) {
            this.lastRequest = request;
            return new CandidateBindingResult(
                    "single_candidate",
                    "test",
                    request.query(),
                    request.candidateType(),
                    List.of(new CandidateItem(nodeId, null, name, request.candidateType(), 0L, "", "", "")),
                    "已匹配到 1 个候选，等待用户确认。"
            );
        }
    }
}
