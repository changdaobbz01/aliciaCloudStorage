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
        assertThat(port.lastRequest.candidateType()).isEqualTo("FILE");
        assertThat(port.lastRequest.queryRole()).isEqualTo("target_name");
        assertThat(port.lastRequest.query()).isEqualTo("临时截图");
        assertThat(port.lastRequest.authorizationHeader()).isEqualTo("Bearer token");
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
