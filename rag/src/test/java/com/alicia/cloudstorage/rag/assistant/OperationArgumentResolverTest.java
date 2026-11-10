package com.alicia.cloudstorage.rag.assistant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationArgumentResolverTest {

    private final OperationArgumentResolver resolver = new OperationArgumentResolver();

    @Test
    void treatsIndefiniteFileAsMissingMutationTarget() {
        OperationArgumentResolver.Resolution resolution = resolver
                .resolve("删除一个文件", "DELETE")
                .orElseThrow();

        assertThat(resolution.source().nameKind())
                .isEqualTo(OperationArgumentResolver.NameKind.GENERIC);
        assertThat(resolution.source().name()).isBlank();
        assertThat(resolution.needsClarification()).isTrue();
    }

    @Test
    void keepsNoisyExtensionMoveAsAnExplicitCollection() {
        OperationArgumentResolver.Resolution resolution = resolver
                .resolve("所有 .png 的文件，都给我挪到素材库里", "MOVE")
                .orElseThrow();

        assertThat(resolution.source().quantifier())
                .as("parsed source: %s", resolution.source())
                .isEqualTo(OperationArgumentResolver.Quantifier.EXPLICIT_ALL);
        assertThat(resolution.destination().surface()).isEqualTo("素材库");
    }

    @Test
    void parsesQuotedColloquialRenameWithoutModelHelp() {
        OperationArgumentResolver.Resolution resolution = resolver
                .resolve("那个《月报.xlsx》给我改个名儿，叫月报-终版.xlsx", "RENAME")
                .orElseThrow();

        assertThat(resolution.source().name()).isEqualTo("月报.xlsx");
        assertThat(resolution.source().resultType()).isEqualTo("FILE");
        assertThat(resolution.newName()).isEqualTo("月报-终版.xlsx");
        assertThat(resolution.needsClarification()).isFalse();
    }

    @Test
    void distinguishesImplicitCategorySetsFromExplicitSingleItems() {
        OperationArgumentResolver.Resolution category = resolver
                .resolve("视频文件移到影音资料库", "MOVE")
                .orElseThrow();
        OperationArgumentResolver.Resolution single = resolver
                .resolve("把某个视频移到待处理目录", "MOVE")
                .orElseThrow();

        assertThat(category.source().nameKind()).isEqualTo(OperationArgumentResolver.NameKind.GENERIC);
        assertThat(category.source().category()).isEqualTo("VIDEO");
        assertThat(category.source().quantifier()).isEqualTo(OperationArgumentResolver.Quantifier.UNSPECIFIED);

        assertThat(single.source().nameKind()).isEqualTo(OperationArgumentResolver.NameKind.GENERIC);
        assertThat(single.source().category()).isEqualTo("VIDEO");
        assertThat(single.source().quantifier()).isEqualTo(OperationArgumentResolver.Quantifier.SINGLE);
    }

    @Test
    void normalizesCategoryTyposAndClassifiersWithoutLosingSingleItemBoundaries() {
        OperationArgumentResolver.Resolution document = resolver
                .resolve("文档资料放到数据资料区", "MOVE")
                .orElseThrow();
        OperationArgumentResolver.Resolution typo = resolver
                .resolve("视屏文件都放进影音资料库", "MOVE")
                .orElseThrow();
        OperationArgumentResolver.Resolution single = resolver
                .resolve("把一张照片移动到待选区", "MOVE")
                .orElseThrow();

        assertThat(document.source().category()).isEqualTo("DOCUMENT");
        assertThat(typo.source().category()).isEqualTo("VIDEO");
        assertThat(single.source().category()).isEqualTo("IMAGE");
        assertThat(single.source().quantifier()).isEqualTo(OperationArgumentResolver.Quantifier.SINGLE);
    }
}
