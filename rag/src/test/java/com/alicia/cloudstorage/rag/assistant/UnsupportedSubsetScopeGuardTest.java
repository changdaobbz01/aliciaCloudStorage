package com.alicia.cloudstorage.rag.assistant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnsupportedSubsetScopeGuardTest {

    private final UnsupportedSubsetScopeGuard guard = new UnsupportedSubsetScopeGuard();

    @Test
    void blocksUnsupportedCardinalityWithoutLosingTheOperation() {
        assertDecision("把最新四份文档搬到备份区", "node_move", "MOVE");
        assertDecision("清理最早三个日志", "file_delete", "DELETE");
        assertDecision("删除最早的两个备份文件", "file_delete", "DELETE");
        assertDecision("随机挑两张照片放进抽查目录", "node_move", "MOVE");
        assertDecision("把第三至第六个候选移至评审区", "node_move", "MOVE");
        assertDecision("给前两个文件生成分享链接", "file_share", "SHARE");
    }

    @Test
    void blocksExclusionsAndKeepsSupportedPositiveSelectorShape() {
        UnsupportedSubsetScopeGuard.Decision extension = guard
                .evaluate("把全部 .bak 文件移到备份区，但 stable.bak 不要动")
                .orElseThrow();
        UnsupportedSubsetScopeGuard.Decision category = guard
                .evaluate("把照片都归档到相册，封面.jpg除外")
                .orElseThrow();
        UnsupportedSubsetScopeGuard.Decision predicate = guard
                .evaluate("剩下名称包含合同的文件都移到待签区")
                .orElseThrow();
        UnsupportedSubsetScopeGuard.Decision genericRemainder = guard
                .evaluate("除了正式版.docx，其他文档都移到归档区")
                .orElseThrow();

        assertThat(extension.intentId()).isEqualTo("collection_move_by_extension");
        assertThat(extension.operation()).isEqualTo("MOVE");
        assertThat(extension.entities())
                .containsEntry("extension", "BAK")
                .containsEntry("target_folder", "备份区");
        assertThat(category.intentId()).isEqualTo("collection_move_by_category");
        assertThat(category.entities()).containsEntry("file_type", "照片");
        assertThat(predicate.intentId()).isEqualTo("collection_move_by_name");
        assertThat(predicate.entities()).containsEntry("target_name", "合同");
        assertThat(genericRemainder.intentId()).isEqualTo("node_move");
    }

    @Test
    void ignoresSubsetWordsInsideLiteralNamesAndPositiveSelectors() {
        List.of(
                "删除文件“最近三个版本.txt”",
                "创建一个名为“其余材料”的文件夹",
                "搜索文件名包含“除外”的文档",
                "把所有视频移到影音目录",
                "把当前目录下全部 .webp 文件移到素材库"
        ).forEach(message -> assertThat(guard.evaluate(message)).as(message).isEmpty());
        assertThat(guard.evaluate("如果以后只移动最近一周的日志，你能先给方案吗")).isEmpty();
    }

    private void assertDecision(String message, String intentId, String operation) {
        UnsupportedSubsetScopeGuard.Decision decision = guard.evaluate(message).orElseThrow();
        assertThat(decision.id()).isEqualTo("unsupported_bounded_subset");
        assertThat(decision.intentId()).isEqualTo(intentId);
        assertThat(decision.operation()).isEqualTo(operation);
    }
}
