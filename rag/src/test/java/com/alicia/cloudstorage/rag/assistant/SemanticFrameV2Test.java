package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticFrameV2Test {

    private IntentRecognitionService service;

    @BeforeEach
    void setUp() {
        RagConfigLoader configLoader = new RagConfigLoader(new ObjectMapper());
        IntentRouter intentRouter = new IntentRouter(configLoader, new EntityExtractor(configLoader));
        service = new IntentRecognitionService(
                message -> Optional.empty(),
                intentRouter,
                configLoader
        );
    }

    @Test
    void parsesNameContainsFolderSearchWithoutDowngradingToDirectoryList() {
        IntentRecognitionResponse response = service.recognize("你列出名字带测试的文件夹");

        assertThat(response.intentId()).isEqualTo("file_search");
        assertThat(response.semanticFrame().operation()).isEqualTo("SEARCH");
        assertThat(response.semanticFrame().query().mode()).isEqualTo("NAME_CONTAINS");
        assertThat(response.semanticFrame().query().resultType()).isEqualTo("FOLDER");
        assertThat(response.semanticFrame().query().nameSurface()).isEqualTo("测试");
        assertThat(response.semanticFrame().scope().type()).isEqualTo("ALL");
        assertThat(response.entities())
                .containsEntry("query_mode", "name_search")
                .containsEntry("result_type", "FOLDER")
                .containsEntry("target_name", "测试");
    }

    @Test
    void parsesNamedFolderChildrenAndPreservesFolderSurface() {
        IntentRecognitionResponse response = service.recognize("列出测试目录下的文件");

        assertThat(response.semanticFrame().query().mode()).isEqualTo("LIST_CHILDREN");
        assertThat(response.semanticFrame().query().resultType()).isEqualTo("FILE");
        assertThat(response.semanticFrame().scope().type()).isEqualTo("NAMED_FOLDER");
        assertThat(response.semanticFrame().scope().folderSurface()).isEqualTo("测试目录");
        assertThat(response.entities())
                .containsEntry("query_mode", "directory_list")
                .containsEntry("scope", "named_folder")
                .containsEntry("target_folder", "测试目录")
                .doesNotContainKey("target_name");
    }

    @Test
    void uploadDestinationKeepsExactSurfaceName() {
        IntentRecognitionResponse response = service.recognize(
                "将这个文件上传到测试目录",
                null,
                new AssistantClientContext(null, "/", Map.of("files", 1))
        );

        assertThat(response.semanticFrame().operation()).isEqualTo("UPLOAD");
        assertThat(response.semanticFrame().scope().type()).isEqualTo("NAMED_FOLDER");
        assertThat(response.semanticFrame().scope().folderSurface()).isEqualTo("测试目录");
        assertThat(response.entities()).containsEntry("target_folder", "测试目录");
    }

    @Test
    void unknownInputProducesTargetedClarificationAndSuggestions() {
        IntentRecognitionResponse response = service.recognize("测试目录呢？");

        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.assistantText()).contains("再明确一点");
        assertThat(response.semanticFrame().ambiguities()).contains("operation");
        assertThat(response.semanticFrame().clarification().suggestions()).hasSizeBetween(1, 3);
    }
}
