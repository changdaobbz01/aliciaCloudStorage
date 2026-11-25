package com.alicia.cloudstorage.rag.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OperationArgumentResolverTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "把交接表.xlsx移到「入库或者交接」",
            "把《移到备份或者复核.txt》移动到档案目录",
            "move audit.csv to \"Red or Blue\"",
            "把合同.pdf移到核对目录，备注写归档或者留存",
            "目的地不是暂存，是复核目录"
    })
    void moveDestinationGuardIgnoresLiteralNamesAndUnrelatedClauses(String message) {
        assertThat(new OperationArgumentResolver().hasConflictingMoveDestinations(message, true, false)).isFalse();
    }

    @Test
    void moveDestinationSlotDoesNotAcceptTwoOptions() {
        assertThat(new OperationArgumentResolver().hasConflictingMoveDestinations(
                "「本周清点」和「已入库」里我选「本周清点」或者「已入库」", true, true
        )).isTrue();
    }

    private final OperationArgumentResolver resolver = new OperationArgumentResolver();

    @Test
    void preservesSpacesNestedQuotesAndCommandWordsInMoveArguments() {
        OperationArgumentResolver.Resolution move = resolver
                .directQuotedMove("不用改名，只把『「移到  复核」.pdf』移动到「入库  交接」。")
                .orElseThrow();

        assertThat(move.source().name()).isEqualTo("「移到  复核」.pdf");
        assertThat(move.destination().surface()).isEqualTo("入库  交接");
        assertThat(move.destination().literal()).isTrue();
        assertThat(resolver.directQuotedMove("不要把『「移到  复核」.pdf』移动到「入库  交接」。")).isEmpty();
    }

    @Test
    void removesAnAffirmativeMoveMarkerOnlyOutsideTheQuotedSource() {
        var move = resolver.directQuotedMove("《回单要》文件想要移到「验收  夜班」。").orElseThrow();

        assertThat(move.source().name()).isEqualTo("回单要");
        assertThat(move.source().resultType()).isEqualTo("FILE");
        assertThat(move.destination().surface()).isEqualTo("验收  夜班");
        assertThat(resolver.directQuotedMove("《回单要》不要移到「验收  夜班」。")).isEmpty();
        assertThat(resolver.literalMoveSource("这份文件要移到「验收  夜班」。")).isEmpty();
    }

    @Test
    void keepsCommasAndInnerQuotesInExplicitFolderNames() {
        var folder = resolver.quotedFolderCreation("在当前目录新建目录，名称是『「别改名，二 次」』。")
                .orElseThrow();

        assertThat(folder.parent()).isEqualTo("当前目录");
        assertThat(folder.name()).isEqualTo("「别改名，二 次」");
        assertThat(resolver.quotedFolderCreation("别创建叫「补件」的文件夹。")).isEmpty();
    }

    @Test
    void isolatesAQuotedUploadDestinationFromAnAttachmentStatus() {
        var upload = resolver.resolve("附件传进『“墨尺，  交接”』目录里，本地文件我晚点再选。", "UPLOAD")
                .orElseThrow();
        assertThat(upload.destination().literal()).isTrue();
        assertThat(upload.destination().surface()).isEqualTo("“墨尺，  交接”");
        assertThat(resolver.resolve("附件传进「墨尺交接」，或者「样页归档」。", "UPLOAD")
                .orElseThrow().destination().literal()).isFalse();
    }

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
    void keepsScopedExtensionMoveAsAnExplicitCollection() {
        OperationArgumentResolver.Resolution resolution = resolver
                .resolve("把当前目录所有 .log 文件移动到日志归档目录", "MOVE")
                .orElseThrow();

        assertThat(resolution.source().nameKind())
                .isEqualTo(OperationArgumentResolver.NameKind.GENERIC);
        assertThat(resolution.source().quantifier())
                .isEqualTo(OperationArgumentResolver.Quantifier.EXPLICIT_ALL);
        assertThat(resolution.source().scopeType()).isEqualTo("CURRENT");
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

    @Test
    void recognizesScatteredCategoryWordingAsACollectionSelector() {
        OperationArgumentResolver.Resolution resolution = resolver
                .resolve("散落的音乐统一放到声音仓库文件夹", "MOVE")
                .orElseThrow();

        assertThat(resolution.source().category()).isEqualTo("AUDIO");
        assertThat(resolution.source().quantifier()).isEqualTo(OperationArgumentResolver.Quantifier.EXPLICIT_ALL);
    }

    @Test
    void parsesCanonicalEnglishMoveWithoutLosingEitherRole() {
        OperationArgumentResolver.Resolution resolution = resolver
                .resolve("move 报价单.pdf to 待发送目录", "MOVE")
                .orElseThrow();

        assertThat(resolution.source().name()).isEqualTo("报价单.pdf");
        assertThat(resolution.source().resultType()).isEqualTo("FILE");
        assertThat(resolution.destination().surface()).isEqualTo("待发送目录");
        assertThat(resolution.needsClarification()).isFalse();
    }

    @Test
    void englishSentencePunctuationDoesNotInvalidateTheQuotedDestination() {
        var move = resolver.directQuotedMove("Please move \"Night  count.pdf\" to \"Shelf  B\".").orElseThrow();

        assertThat(move.source().name()).isEqualTo("Night  count.pdf");
        assertThat(move.destination().surface()).isEqualTo("Shelf  B");
        assertThat(move.destination().literal()).isTrue();
    }
}
