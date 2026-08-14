package com.alicia.cloudstorage.rag.assistant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionSourceExpressionParserTest {

    private final CollectionSourceExpressionParser parser = new CollectionSourceExpressionParser();

    @ParameterizedTest
    @ValueSource(strings = {
            "根目录下所有文件和文件夹",
            "根目录下所有文件夹和文件",
            "根目录下全部文件与目录",
            "根目录中的文件、文件夹都",
            "根目录内所有目录以及文件",
            "根目录下的所有文件和文件夹"
    })
    void parsesRootFileAndFolderUnionWithoutDependingOnWordOrder(String message) {
        CollectionSourceExpressionParser.SourceSelection selection = parser.parse(message, null).orElseThrow();

        assertThat(selection.kind()).isEqualTo(CollectionSourceExpressionParser.SOURCE_ROOT);
        assertThat(selection.nodeKinds()).containsExactlyInAnyOrder(
                CollectionSourceExpressionParser.NodeKind.FILE,
                CollectionSourceExpressionParser.NodeKind.FOLDER
        );
        assertThat(selection.recursive()).isFalse();
        assertThat(selection.quantifier()).isEqualTo("ALL");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "根目录下所有文件",
            "测试目录中的全部文件",
            "测试目录所有文件全"
    })
    void preservesFileOnlySelectors(String message) {
        CollectionSourceExpressionParser.SourceSelection selection = parser.parse(message, null).orElseThrow();

        assertThat(selection.nodeKinds()).isEqualTo(Set.of(CollectionSourceExpressionParser.NodeKind.FILE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "根目录下所有文件夹",
            "测试目录中的全部目录"
    })
    void preservesFolderOnlySelectors(String message) {
        CollectionSourceExpressionParser.SourceSelection selection = parser.parse(message, null).orElseThrow();

        assertThat(selection.nodeKinds()).isEqualTo(Set.of(CollectionSourceExpressionParser.NodeKind.FOLDER));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "资料中心目录下所有文件",
            "内部资料文件夹里的全部文件和文件夹",
            "下载目录的所有文件",
            "目录备份文件夹所有文件"
    })
    void validatesObjectExpressionBeforeChoosingFolderBoundary(String message) {
        CollectionSourceExpressionParser.SourceSelection selection = parser.parse(message, null).orElseThrow();

        assertThat(selection.kind()).isEqualTo(CollectionSourceExpressionParser.SOURCE_NAMED_FOLDER);
        assertThat(selection.folder()).isIn("资料中心目录", "内部资料文件夹", "下载目录", "目录备份文件夹");
    }

    @ParameterizedTest
    @ValueSource(strings = {"当前目录下所有文件", "当前文件夹中的全部文件和文件夹"})
    void currentFolderDoesNotRequirePreviousConversationCandidates(String message) {
        CollectionSourceExpressionParser.SourceSelection selection = parser.parse(message, null).orElseThrow();

        assertThat(selection.kind()).isEqualTo(CollectionSourceExpressionParser.SOURCE_CURRENT_FOLDER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "根目录的文件",
            "测试目录中的文件",
            "当前文件夹里的文件和文件夹"
    })
    void preservesImplicitCollectionQuantifierInsteadOfPromotingToAll(String message) {
        CollectionSourceExpressionParser.SourceSelection selection = parser.parse(message, null).orElseThrow();

        assertThat(selection.quantifier()).isEqualTo("IMPLICIT_SET");
    }

    @ParameterizedTest
    @ValueSource(strings = {"文件", "项目", "结果"})
    void genericNounsDoNotPretendToReferencePreviousResults(String message) {
        assertThat(parser.parse(message, null)).isEmpty();
    }
}
