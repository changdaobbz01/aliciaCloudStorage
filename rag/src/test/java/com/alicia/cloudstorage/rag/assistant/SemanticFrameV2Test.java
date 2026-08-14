package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {
            "分享文件",
            "分享根目录文件",
            "将根目录的文件进行分享"
    })
    void genericShareTargetsRequireClarificationInsteadOfFakeNameSearch(String message) {
        IntentRecognitionResponse response = service.recognize(message);

        assertThat(response.intentId()).isEqualTo("file_share");
        assertThat(response.semanticFrame().operation()).isEqualTo("SHARE");
        assertThat(response.semanticFrame().query().nameSurface()).isBlank();
        assertThat(response.entities()).doesNotContainKey("target_name");
        assertThat(response.missingSlots()).contains("target_name");
        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.actionDraft().type()).isEqualTo("none");
    }

    @Test
    void scopedGenericSharePreservesRootScopeAndFileType() {
        IntentRecognitionResponse response = service.recognize("将根目录的文件进行分享");

        assertThat(response.semanticFrame().scope().type()).isEqualTo("ROOT");
        assertThat(response.semanticFrame().query().resultType()).isEqualTo("FILE");
        assertThat(response.entities())
                .containsEntry("scope", "root")
                .containsEntry("result_type", "FILE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "分享名为文件的文件",
            "分享名为根目录文件的文件"
    })
    void explicitNamingEvidenceAllowsGenericLookingFileNames(String message) {
        IntentRecognitionResponse response = service.recognize(message);

        assertThat(response.intentId()).isEqualTo("file_share");
        assertThat(response.semanticFrame().query().nameSurface()).isIn("文件", "根目录文件");
        assertThat(response.missingSlots()).doesNotContain("target_name");
    }

    @Test
    void scopedExactShareKeepsNameAndRootScope() {
        IntentRecognitionResponse response = service.recognize("分享根目录下的合同.pdf");

        assertThat(response.semanticFrame().query().nameSurface()).isEqualTo("合同.pdf");
        assertThat(response.semanticFrame().scope().type()).isEqualTo("ROOT");
        assertThat(response.entities()).containsEntry("scope", "root");
    }

    @Test
    void genericDeleteTargetDoesNotBecomeAFileName() {
        IntentRecognitionResponse response = service.recognize("删除文件");

        assertThat(response.intentId()).isEqualTo("file_delete");
        assertThat(response.semanticFrame().query().nameSurface()).isBlank();
        assertThat(response.missingSlots()).contains("target_name");
        assertThat(response.nextAction()).isEqualTo("ask_clarification");
    }

    @Test
    void stripsClassifierWrappersButPreservesExplicitLiteralNames() {
        IntentRecognitionResponse wrappedFolder = service.recognize("删除测试目录这个文件夹");
        IntentRecognitionResponse classifiedName = service.recognize("把文件项目文档改名为项目文档最终版.docx");
        IntentRecognitionResponse explicitLiteral = service.recognize("删除名为文件项目文档的文件");

        assertThat(wrappedFolder.entities()).containsEntry("target_name", "测试目录");
        assertThat(classifiedName.entities()).containsEntry("target_name", "项目文档");
        assertThat(explicitLiteral.entities()).containsEntry("target_name", "文件项目文档");
    }

    @Test
    void moveKeepsDestinationWhenSourceIsGeneric() {
        IntentRecognitionResponse response = service.recognize("把文件移动到资料");

        assertThat(response.intentId()).isEqualTo("node_move");
        assertThat(response.entities()).containsEntry("target_folder", "资料");
        assertThat(response.entities()).doesNotContainKey("target_name");
        assertThat(response.missingSlots()).contains("target_name");
        assertThat(response.missingSlots()).doesNotContain("target_folder");
    }

    @Test
    void renameKeepsNewNameWhenSourceIsGeneric() {
        IntentRecognitionResponse response = service.recognize("把文件重命名为报告");

        assertThat(response.intentId()).isEqualTo("file_rename");
        assertThat(response.entities()).containsEntry("new_name", "报告");
        assertThat(response.entities()).doesNotContainKey("target_name");
        assertThat(response.missingSlots()).contains("target_name");
        assertThat(response.missingSlots()).doesNotContain("new_name");
    }

    @Test
    void uploadTreatsFileAsClientInputAndRootAsDestination() {
        IntentRecognitionResponse response = service.recognize(
                "把文件上传到根目录",
                null,
                new AssistantClientContext(null, "/", Map.of("files", 1))
        );

        assertThat(response.intentId()).isEqualTo("file_upload");
        assertThat(response.semanticFrame().operation()).isEqualTo("UPLOAD");
        assertThat(response.semanticFrame().scope().type()).isEqualTo("ROOT");
        assertThat(response.entities()).containsEntry("target_folder", "根目录");
        assertThat(response.entities()).doesNotContainKey("target_name");
    }

    @Test
    void batchShareIsRejectedBeforeCandidateBinding() {
        IntentRecognitionResponse response = service.recognize("分享根目录下所有文件");

        assertThat(response.intentId()).isEqualTo("file_share");
        assertThat(response.semanticFrame().ambiguities()).contains("batch_share_unsupported");
        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.assistantText()).contains("一次只能分享一个");
    }

    @Test
    void categoryDeleteRequiresExplicitCollectionQuantifier() {
        IntentRecognitionResponse implicit = service.recognize("删除图片");
        IntentRecognitionResponse explicit = service.recognize("删除所有图片");
        IntentRecognitionResponse trailing = service.recognize("帮我将视频类型文件全部删除");

        assertThat(implicit.intentId()).isEqualTo("file_delete");
        assertThat(implicit.nextAction()).isEqualTo("ask_clarification");
        assertThat(implicit.actionDraft().type()).isEqualTo("none");
        assertThat(implicit.semanticFrame().ambiguities()).contains("source_target_required");

        assertThat(explicit.intentId()).isEqualTo("collection_delete_by_category");
        assertThat(explicit.nextAction()).isEqualTo("wait_for_backend_binding");
        assertThat(explicit.actionDraft().type()).isEqualTo("collection.trash_by_category");
        assertThat(trailing.intentId()).isEqualTo("collection_delete_by_category");
        assertThat(trailing.nextAction()).isEqualTo("wait_for_backend_binding");
        assertThat(trailing.actionDraft().type()).isEqualTo("collection.trash_by_category");
    }

    @Test
    void quantifierAndScopeCharactersInsideLiteralNamesRemainAtomic() {
        IntentRecognitionResponse city = service.recognize("删除成都");
        IntentRecognitionResponse ownership = service.recognize("删除所有权协议.pdf");
        IntentRecognitionResponse rootGuide = service.recognize("分享根目录说明.pdf");
        IntentRecognitionResponse trailingQuantifier = service.recognize("删除视频全部说明.pdf");
        IntentRecognitionResponse leadingQuantifier = service.recognize("删除所有图片说明.pdf");
        IntentRecognitionResponse explicitName = service.recognize("删除名为所有图片的文件");
        IntentRecognitionResponse moveLiteral = service.recognize("把视频全部说明.pdf移动到资料");

        assertThat(city.entities()).containsEntry("target_name", "成都");
        assertThat(ownership.entities()).containsEntry("target_name", "所有权协议.pdf");
        assertThat(rootGuide.entities()).containsEntry("target_name", "根目录说明.pdf");
        assertThat(rootGuide.semanticFrame().scope().type()).isEqualTo("ALL");
        assertThat(trailingQuantifier.intentId()).isEqualTo("file_delete");
        assertThat(trailingQuantifier.entities()).containsEntry("target_name", "视频全部说明.pdf");
        assertThat(trailingQuantifier.actionDraft().type()).isEqualTo("delete");
        assertThat(leadingQuantifier.intentId()).isEqualTo("file_delete");
        assertThat(leadingQuantifier.entities()).containsEntry("target_name", "所有图片说明.pdf");
        assertThat(explicitName.intentId()).isEqualTo("file_delete");
        assertThat(explicitName.entities()).containsEntry("target_name", "所有图片");
        assertThat(moveLiteral.intentId()).isEqualTo("node_move");
        assertThat(moveLiteral.entities())
                .containsEntry("target_name", "视频全部说明.pdf")
                .containsEntry("target_folder", "资料");
    }

    @Test
    void uploadRejectsCloudNodeAsLocalInputSource() {
        IntentRecognitionResponse response = service.recognize(
                "把根目录下的合同.pdf上传到资料目录",
                null,
                new AssistantClientContext(null, "/", Map.of("files", 1))
        );
        IntentRecognitionResponse leadingUpload = service.recognize(
                "上传云盘中的合同.pdf到资料目录",
                null,
                new AssistantClientContext(null, "/", Map.of("files", 1))
        );

        assertThat(response.intentId()).isEqualTo("file_upload");
        assertThat(response.semanticFrame().ambiguities()).contains("upload_source_must_be_client_input");
        assertThat(response.entities()).containsEntry("target_folder", "资料目录");
        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(leadingUpload.semanticFrame().ambiguities()).contains("upload_source_must_be_client_input");
        assertThat(leadingUpload.entities()).containsEntry("target_folder", "资料目录");
        assertThat(leadingUpload.nextAction()).isEqualTo("ask_clarification");
        assertThat(leadingUpload.actionDraft().type()).isEqualTo("none");
    }

    @Test
    void renameRequiresControlledSingleOrPrefixStrategy() {
        IntentRecognitionResponse missingName = service.recognize("重命名合同.pdf");
        IntentRecognitionResponse unsupportedBatch = service.recognize("把所有图片统一重命名为归档");

        assertThat(missingName.entities()).containsEntry("target_name", "合同.pdf");
        assertThat(missingName.semanticFrame().ambiguities()).contains("new_name_required");
        assertThat(missingName.nextAction()).isEqualTo("ask_clarification");
        assertThat(missingName.actionDraft().type()).isEqualTo("none");
        assertThat(unsupportedBatch.semanticFrame().ambiguities())
                .contains("batch_rename_strategy_unsupported");
        assertThat(unsupportedBatch.nextAction()).isEqualTo("ask_clarification");
        assertThat(unsupportedBatch.actionDraft().type()).isEqualTo("none");
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
