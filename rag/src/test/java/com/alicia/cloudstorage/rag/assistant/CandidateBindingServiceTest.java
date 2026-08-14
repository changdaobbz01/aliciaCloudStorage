package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateBindingServiceTest {

    private RagConfigLoader configLoader;
    private IntentRouter intentRouter;
    private IntentRecognitionService intentRecognitionService;

    @BeforeEach
    void setUp() {
        configLoader = new RagConfigLoader(new ObjectMapper());
        intentRouter = new IntentRouter(configLoader, new EntityExtractor(configLoader));
        IntentModelClient unavailableClient = message -> Optional.empty();
        intentRecognitionService = new IntentRecognitionService(unavailableClient, intentRouter, configLoader);
    }

    @Test
    void skipsBindingWhenClarificationIsStillNeeded() {
        CapturingCandidateSearchPort port = new CapturingCandidateSearchPort();
        CandidateBindingService service = new CandidateBindingService(port, intentRouter, 5);

        IntentRecognitionResponse response = intentRecognitionService.recognize("删除");

        CandidateBindingResult result = service.bind(response, "Bearer token");

        assertThat(result.status()).isEqualTo("waiting_for_clarification");
        assertThat(port.lastRequest).isNull();
    }

    @Test
    void skipsBindingForMessageOnlyFallbackEvenWhenClarificationIsRequested() {
        CapturingCandidateSearchPort port = new CapturingCandidateSearchPort();
        CandidateBindingService service = new CandidateBindingService(port, intentRouter, 5);

        IntentRecognitionResponse response = intentRecognitionService.recognize("今天的风有点大");

        CandidateBindingResult result = service.bind(response, "Bearer token");

        assertThat(response.intentId()).isEqualTo("fallback");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(result.status()).isEqualTo("not_requested");
        assertThat(port.lastRequest).isNull();
    }

    @Test
    void buildsFileCandidateSearchRequestFromTargetName() {
        CapturingCandidateSearchPort port = new CapturingCandidateSearchPort();
        CandidateBindingService service = new CandidateBindingService(port, intentRouter, 5);

        IntentRecognitionResponse response = intentRecognitionService.recognize("删除临时截图");

        CandidateBindingResult result = service.bind(response, "Bearer token");

        assertThat(result.status()).isEqualTo("single_candidate");
        assertThat(port.lastRequest.intentId()).isEqualTo("file_delete");
        assertThat(port.lastRequest.candidateType()).isEqualTo("ANY");
        assertThat(port.lastRequest.queryRole()).isEqualTo("target_name");
        assertThat(port.lastRequest.query()).isEqualTo("临时截图");
        assertThat(port.lastRequest.authorizationHeader()).isEqualTo("Bearer token");
    }

    @Test
    void genericShareNeverReachesStorageSearch() {
        CapturingCandidateSearchPort port = new CapturingCandidateSearchPort();
        CandidateBindingService service = new CandidateBindingService(port, intentRouter, 5);

        IntentRecognitionResponse response = intentRecognitionService.recognize("将根目录的文件进行分享");
        CandidateBindingResult result = service.bind(response, "Bearer token");

        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(result.status()).isEqualTo("not_requested");
        assertThat(port.lastRequest).isNull();
    }

    @Test
    void semanticAmbiguityBlocksBindingEvenWhenLegacyFieldsRequestIt() {
        CapturingCandidateSearchPort port = new CapturingCandidateSearchPort();
        CandidateBindingService service = new CandidateBindingService(port, intentRouter, 5);
        IntentRecognitionResponse ready = intentRecognitionService.recognize("分享合同.pdf");
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

        CandidateBindingResult result = service.bind(contradictory, "Bearer token");

        assertThat(contradictory.nextAction()).isEqualTo("wait_for_backend_binding");
        assertThat(result.status()).isEqualTo("waiting_for_clarification");
        assertThat(port.lastRequest).isNull();
    }

    @Test
    void exactShareUsesValidatedRootSourceScope() {
        CapturingCandidateSearchPort port = new CapturingCandidateSearchPort();
        CandidateBindingService service = new CandidateBindingService(port, intentRouter, 5);

        IntentRecognitionResponse response = intentRecognitionService.recognize("分享根目录下的合同.pdf");
        service.bind(response, "Bearer token");

        assertThat(port.lastRequest.query()).isEqualTo("合同.pdf");
        assertThat(port.lastRequest.scope()).isEqualTo("root");
        assertThat(port.lastRequest.candidateType()).isEqualTo("FILE");
    }

    @Test
    void exactMoveKeepsSourceScopeButBindsDestinationFolder() {
        CapturingCandidateSearchPort port = new CapturingCandidateSearchPort();
        CandidateBindingService service = new CandidateBindingService(port, intentRouter, 5);

        IntentRecognitionResponse response = intentRecognitionService.recognize(
                "把根目录下的合同.pdf移动到资料目录"
        );
        service.bind(response, "Bearer token");

        assertThat(response.semanticFrame().scope().type()).isEqualTo("ROOT");
        assertThat(response.entities())
                .containsEntry("scope", "root")
                .containsEntry("target_name", "合同.pdf")
                .containsEntry("target_folder", "资料目录");
        assertThat(port.lastRequest.queryRole()).isEqualTo("target_folder");
        assertThat(port.lastRequest.query()).isEqualTo("资料目录");
        assertThat(port.lastRequest.candidateType()).isEqualTo("FOLDER");
    }

    @Test
    void buildsFolderCandidateSearchRequestFromTargetFolder() {
        CapturingCandidateSearchPort port = new CapturingCandidateSearchPort();
        CandidateBindingService service = new CandidateBindingService(port, intentRouter, 5);

        IntentRecognitionResponse response = intentRecognitionService.recognize("上传到项目资料");

        CandidateBindingResult result = service.bind(response, "Bearer token");

        assertThat(result.status()).isEqualTo("single_candidate");
        assertThat(port.lastRequest.intentId()).isEqualTo("file_upload");
        assertThat(port.lastRequest.candidateType()).isEqualTo("FOLDER");
        assertThat(port.lastRequest.queryRole()).isEqualTo("target_folder");
        assertThat(port.lastRequest.query()).isEqualTo("项目资料");
    }

    @Test
    void resolvesConfiguredCloudDriveRootWithoutCallingStorageSearch() {
        CapturingCandidateSearchPort port = new CapturingCandidateSearchPort();
        CandidateBindingService service = new CandidateBindingService(port, intentRouter, 5);

        IntentRecognitionResponse response = intentRecognitionService.recognize("把这些文件上传到根目录");
        CandidateBindingResult result = service.bind(response, "Bearer token");

        assertThat(response.intentId()).isEqualTo("file_upload");
        assertThat(result.status()).isEqualTo("single_candidate");
        assertThat(result.source()).isEqualTo("virtual:cloud-drive-root");
        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.nodeId()).isNull();
            assertThat(candidate.name()).isEqualTo("根目录");
            assertThat(candidate.path()).isEqualTo("/");
        });
        assertThat(port.lastRequest).isNull();
    }

    @Test
    void convertsRootFolderListIntoDirectoryQueryWithoutKeyword() {
        CapturingCandidateSearchPort port = new CapturingCandidateSearchPort();
        CandidateBindingService service = new CandidateBindingService(port, intentRouter, 5);

        IntentRecognitionResponse response = intentRecognitionService.recognize("列出根目录文件夹列表");
        service.bind(response, "Bearer token", new AssistantClientContext(88L, "根目录/项目"));

        assertThat(response.provider()).isEqualTo("local_fallback");
        assertThat(response.intentId()).isEqualTo("file_search");
        assertThat(port.lastRequest.queryMode()).isEqualTo("directory_list");
        assertThat(port.lastRequest.scope()).isEqualTo("root");
        assertThat(port.lastRequest.candidateType()).isEqualTo("FOLDER");
        assertThat(port.lastRequest.query()).isBlank();
        assertThat(port.lastRequest.maxResults()).isEqualTo(50);
        assertThat(port.lastRequest.currentFolderId()).isEqualTo(88L);
        assertThat(response.entities())
                .containsEntry("query_mode", "directory_list")
                .containsEntry("scope", "root")
                .containsEntry("result_type", "FOLDER")
                .doesNotContainKey("target_name");
    }

    @Test
    void keepsCurrentDirectoryContextSeparateFromNaturalLanguage() {
        CapturingCandidateSearchPort port = new CapturingCandidateSearchPort();
        CandidateBindingService service = new CandidateBindingService(port, intentRouter, 5);

        IntentRecognitionResponse response = intentRecognitionService.recognize("显示当前目录的文件");
        service.bind(response, "Bearer token", new AssistantClientContext(42L, "根目录/资料"));

        assertThat(port.lastRequest.queryMode()).isEqualTo("directory_list");
        assertThat(port.lastRequest.scope()).isEqualTo("current");
        assertThat(port.lastRequest.candidateType()).isEqualTo("FILE");
        assertThat(port.lastRequest.currentFolderId()).isEqualTo(42L);
        assertThat(port.lastRequest.currentFolderPath()).isEqualTo("根目录/资料");
        assertThat(port.lastRequest.query()).isBlank();
    }

    @Test
    void preservesFileCategoryForFilterOnlySearch() {
        CapturingCandidateSearchPort port = new CapturingCandidateSearchPort();
        CandidateBindingService service = new CandidateBindingService(port, intentRouter, 5);

        IntentRecognitionResponse response = intentRecognitionService.recognize("列出所有图片文件");
        service.bind(response, "Bearer token");

        assertThat(response.intentId()).isEqualTo("file_search");
        assertThat(response.entities()).containsEntry("file_type", "图片");
        assertThat(response.semanticFrame().query().mode()).isEqualTo("FILTER");
        assertThat(response.semanticFrame().query().filters()).containsEntry("file_type", "图片");
        assertThat(port.lastRequest.queryMode()).isEqualTo("directory_list");
        assertThat(port.lastRequest.scope()).isEqualTo("all");
        assertThat(port.lastRequest.candidateType()).isEqualTo("FILE");
        assertThat(port.lastRequest.category()).isEqualTo("IMAGE");
        assertThat(port.lastRequest.query()).isBlank();
    }

    private static class CapturingCandidateSearchPort implements CandidateSearchPort {
        private CandidateSearchRequest lastRequest;

        @Override
        public CandidateBindingResult search(CandidateSearchRequest request) {
            this.lastRequest = request;
            return new CandidateBindingResult(
                    "single_candidate",
                    "test",
                    request.query(),
                    request.candidateType(),
                    List.of(new CandidateItem(1L, null, request.query(), request.candidateType(), 0L, "", "", "")),
                    "test"
            );
        }
    }
}
