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
    void appliesSharedNamePredicateToDeleteSynonyms() {
        for (String message : java.util.List.of(
                "删除名称带测试的文件",
                "删除名称含测试的文件",
                "删除文件名中带测试的文件",
                "删除名称包含测试的文件"
        )) {
            IntentRecognitionResponse response = service.recognize(message);

            assertThat(response.intentId()).as(message).isEqualTo("collection_delete_by_name");
            assertThat(response.actionDraft().type()).as(message).isEqualTo("collection.trash_by_name_contains");
            assertThat(response.semanticFrame().operation()).as(message).isEqualTo("DELETE");
            assertThat(response.semanticFrame().query().mode()).as(message).isEqualTo("NAME_CONTAINS");
            assertThat(response.semanticFrame().query().resultType()).as(message).isEqualTo("FILE");
            assertThat(response.semanticFrame().query().nameSurface()).as(message).isEqualTo("测试");
            assertThat(response.entities()).as(message)
                    .containsEntry("target_name", "测试")
                    .containsEntry("result_type", "FILE");
        }
    }

    @Test
    void appliesSharedNamePredicateToMoveAndPrefixRename() {
        IntentRecognitionResponse move = service.recognize("把名称带测试的文件移动到文件记录");
        IntentRecognitionResponse rename = service.recognize(
                "将名称带测试的文件重命名，统一在头部加上归档-"
        );

        assertThat(move.intentId()).isEqualTo("collection_move_by_name");
        assertThat(move.actionDraft().type()).isEqualTo("collection.move_by_name_contains");
        assertThat(move.semanticFrame().query().mode()).isEqualTo("NAME_CONTAINS");
        assertThat(move.semanticFrame().query().nameSurface()).isEqualTo("测试");
        assertThat(move.semanticFrame().query().resultType()).isEqualTo("FILE");
        assertThat(move.entities()).containsEntry("target_folder", "文件记录");

        assertThat(rename.intentId()).isEqualTo("collection_rename_add_prefix");
        assertThat(rename.actionDraft().type()).isEqualTo("collection.rename_add_prefix");
        assertThat(rename.semanticFrame().query().mode()).isEqualTo("NAME_CONTAINS");
        assertThat(rename.semanticFrame().query().nameSurface()).isEqualTo("测试");
        assertThat(rename.entities()).containsEntry("rename_prefix", "归档-");
    }

    @Test
    void exactFileNameContainingPredicateWordsStaysAtomic() {
        IntentRecognitionResponse response = service.recognize("删除名为名称带测试的文件");

        assertThat(response.intentId()).isEqualTo("file_delete");
        assertThat(response.actionDraft().type()).isEqualTo("delete");
        assertThat(response.semanticFrame().query().mode()).isNotEqualTo("NAME_CONTAINS");
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
    void stripsReferentialClassifierWithoutTruncatingRealFolderName() {
        IntentRecognitionResponse response = service.recognize("找到测试目录这个文件夹");

        assertThat(response.intentId()).isEqualTo("file_search");
        assertThat(response.semanticFrame().query().nameSurface()).isEqualTo("测试目录");
        assertThat(response.semanticFrame().query().resultType()).isEqualTo("FOLDER");
        assertThat(response.entities()).containsEntry("target_name", "测试目录");
    }

    @Test
    void infersFolderTypeFromDirectorySurfaceName() {
        IntentRecognitionResponse response = service.recognize("打开测试目录");

        assertThat(response.semanticFrame().query().nameSurface()).isEqualTo("测试目录");
        assertThat(response.semanticFrame().query().resultType()).isEqualTo("FOLDER");
    }

    @Test
    void understandsDirectoryContentsWithoutExplicitListVerb() {
        IntentRecognitionResponse response = service.recognize("测试目录中的文件");

        assertThat(response.intentId()).isEqualTo("file_search");
        assertThat(response.semanticFrame().operation()).isEqualTo("SEARCH");
        assertThat(response.semanticFrame().query().mode()).isEqualTo("LIST_CHILDREN");
        assertThat(response.semanticFrame().query().resultType()).isEqualTo("FILE");
        assertThat(response.semanticFrame().scope().type()).isEqualTo("NAMED_FOLDER");
        assertThat(response.semanticFrame().scope().folderSurface()).isEqualTo("测试目录");
    }

    @Test
    void asksForFolderContextWhenDeicticDirectoryHasNoReferent() {
        IntentRecognitionResponse response = service.recognize("这个文件夹中文件有哪些");

        assertThat(response.intentId()).isEqualTo("file_search");
        assertThat(response.semanticFrame().query().mode()).isEqualTo("LIST_CHILDREN");
        assertThat(response.semanticFrame().scope().type()).isEqualTo("PREVIOUS_RESULTS");
        assertThat(response.semanticFrame().ambiguities()).contains("folder_reference");
        assertThat(response.assistantText()).contains("不知道“这个文件夹”指的是哪一个");
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
