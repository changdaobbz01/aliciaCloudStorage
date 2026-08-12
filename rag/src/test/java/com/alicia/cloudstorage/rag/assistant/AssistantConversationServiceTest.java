package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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
        CandidateBindingService candidateBindingService = new CandidateBindingService(candidateSearchPort, intentRouter, 5);
        ConversationContextResolver contextResolver = new ConversationContextResolver(
                (message, conversation, baseResponse) -> Optional.empty(),
                intentRecognitionService,
                configLoader
        );
        return new AssistantConversationService(
                intentRecognitionService,
                intentRouter,
                new AssistantConversationStore(30, 100),
                candidateBindingService,
                new CandidateSelectionService(configLoader),
                contextResolver,
                new BackendActionDraftService(configLoader),
                new ActionPlanService(configLoader),
                new CollectionPreviewService(collectionPreviewPort, 20, 500),
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
        ));

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
        ));

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
        ));
        IntentRecognitionResponse thirdTurn = service.plan(new AssistantPlanRequest(
                "确认",
                secondTurn.conversation().conversationId()
        ));

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
        ));

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
        ));

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
        ));

        assertThat(firstTurn.intentId()).isEqualTo("file_upload");
        assertThat(secondTurn.nextAction()).isEqualTo("handoff_to_client_upload");
        assertThat(secondTurn.conversation().status()).isEqualTo("client_action_required");
        assertThat(secondTurn.backendActionDraft().status()).isEqualTo("client_action_required");
        assertThat(secondTurn.backendActionDraft().executableByBackend()).isFalse();
        assertThat(secondTurn.backendActionDraft().method()).isEqualTo("POST");
        assertThat(secondTurn.backendActionDraft().path()).isEqualTo("/api/storage/files");
        assertThat(secondTurn.backendActionDraft().contentType()).isEqualTo("multipart/form-data");
        assertThat(secondTurn.backendActionDraft().queryParameters()).containsEntry("parentId", 501L);
        assertThat(secondTurn.backendActionDraft().requiredClientFields()).containsExactly("file");
        assertThat(secondTurn.actionPlan().status()).isEqualTo("client_input_required");
        assertThat(secondTurn.actionPlan().actionType()).isEqualTo("file.upload");
        assertThat(secondTurn.actionPlan().requiredClientFields()).containsExactly("files");
        assertThat(secondTurn.actionPlan().steps().getFirst().params()).containsEntry("parentId", 501L);
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
        ));

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
                "把名字带有测试的文件全部删除",
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
        assertThat(previewPort.lastRequest.filter()).containsEntry("nameContains", "测试");
        assertThat(sourceCollection.status()).isEqualTo("resolved");
        assertThat(sourceCollection.candidates()).hasSize(2);
        assertThat(sourceCollection.count()).isEqualTo(2);
        assertThat(sourceCollection.filter()).containsEntry("nameContains", "测试");
        assertThat(sourceCollection.filter()).containsEntry("includeFolders", false);
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().getFirst().action()).isEqualTo("node.batch_trash");
        assertThat(plan.steps().getFirst().params())
                .containsEntry("nodeIds", "$bindings.sourceCollection.nodeIds");
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
        assertThat(previewPort.calls).isEqualTo(2);
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
        assertThat(port.lastRequest.query()).isEqualTo("归档");
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
        assertThat(response.entities()).containsEntry("target_folder", "资料");
        assertThat(port.lastRequest.candidateType()).isEqualTo("FOLDER");
        assertThat(port.lastRequest.query()).isEqualTo("资料");
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
        assertThat(previewPort.calls).isEqualTo(2);
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
        ));

        assertThat(secondTurn.nextAction()).isEqualTo("wait_for_user_confirmation");
        assertThat(secondTurn.conversation().status()).isEqualTo("waiting_for_user_confirmation");
        assertThat(secondTurn.backendActionDraft().status()).isEqualTo("missing_candidate_fields");
        assertThat(secondTurn.backendActionDraft().path()).isBlank();
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
