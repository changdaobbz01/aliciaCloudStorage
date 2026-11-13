package com.alicia.cloudstorage.rag.assistant;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnsupportedTemporalSizeScopeGuardTest {

    private final UnsupportedTemporalSizeScopeGuard guard = new UnsupportedTemporalSizeScopeGuard();

    @Test
    void blocksTemporalSelectorsWithoutLosingTheOperation() {
        assertDecision("把昨天修改的文档移到归档区", "unsupported_temporal_constraint", "node_move", "MOVE");
        assertDecision("删除三十天前的备份文件", "unsupported_temporal_constraint", "file_delete", "DELETE");
        assertDecision("为本周上传的资料生成分享链接", "unsupported_temporal_constraint", "file_share", "SHARE");
        assertDecision("把2026年10月1日之前的日志移到历史目录", "unsupported_temporal_constraint", "node_move", "MOVE");
        assertDecision("清理最近七天内生成的临时文件", "unsupported_temporal_constraint", "file_delete", "DELETE");
        assertDecision("把晚于2026-11-01修改的视频移到待审区", "unsupported_temporal_constraint", "node_move", "MOVE");
    }

    @Test
    void blocksSizeSelectorsWithoutLosingTheOperation() {
        assertDecision("删除大于100MB的视频文件", "unsupported_size_constraint", "file_delete", "DELETE");
        assertDecision("把小于1MB的图片移到缩略图库", "unsupported_size_constraint", "node_move", "MOVE");
        assertDecision("为10MB到50MB的文档生成分享链接", "unsupported_size_constraint", "file_share", "SHARE");
        assertDecision("清理超过2GB的压缩包", "unsupported_size_constraint", "file_delete", "DELETE");
        assertDecision("把不足500KB的日志移到小文件目录", "unsupported_size_constraint", "node_move", "MOVE");
        assertDecision("删除大小等于0字节的空文件", "unsupported_size_constraint", "file_delete", "DELETE");
    }

    @Test
    void ignoresQuotedLiteralsAndUnrelatedConversation() {
        List.of(
                "删除文件“昨天的会议纪要.docx”",
                "创建一个名为“大于100MB”的文件夹",
                "搜索文件名包含“最近七天”的文档",
                "把文件“2026-10-01之前.log”移到归档区",
                "把文件“小于1MB.txt”重命名为“说明.txt”",
                "在归档区下新建本月发票文件夹并上传",
                "在共享区下新建本月发票文件夹并上传",
                "今天有点累，先聊会儿"
        ).forEach(message -> assertThat(guard.evaluate(message)).as(message).isEmpty());
    }

    private void assertDecision(String message, String id, String intentId, String operation) {
        UnsupportedTemporalSizeScopeGuard.Decision decision = guard.evaluate(message).orElseThrow();
        assertThat(decision.id()).isEqualTo(id);
        assertThat(decision.intentId()).isEqualTo(intentId);
        assertThat(decision.operation()).isEqualTo(operation);
    }
}
