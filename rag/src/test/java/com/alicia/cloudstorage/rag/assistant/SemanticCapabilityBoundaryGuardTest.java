package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticCapabilityBoundaryGuardTest {

    private SemanticCapabilityBoundaryGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SemanticCapabilityBoundaryGuard(new RagConfigLoader(new ObjectMapper()));
    }

    @Test
    void blocksConditionalCreateThenOrganizeRequest() {
        SemanticCapabilityBoundaryGuard.BoundaryDecision decision = guard
                .evaluate("如果图片目录不存在就先新建，然后把图片全部放进去")
                .orElseThrow();

        assertThat(decision.id()).isEqualTo("conditional_create_then_organize");
        assertThat(decision.userMessage()).contains("先让我新建目标文件夹");
    }

    @Test
    void blocksUnsupportedBatchRenameFormat() {
        assertThat(guard.evaluate("把这些文件按日期和顺序编号统一重命名"))
                .get()
                .extracting(SemanticCapabilityBoundaryGuard.BoundaryDecision::id)
                .isEqualTo("unsupported_batch_rename_rule");
    }

    @Test
    void allowsSupportedCreateThenUploadAndPrefixRename() {
        assertThat(guard.evaluate("新建项目资料文件夹，然后把我选的本地文件上传进去"))
                .isEmpty();
        assertThat(guard.evaluate("把名称包含测试的文件统一添加前缀归档_"))
                .isEmpty();
    }

    @Test
    void allowsSupportedMoveSelectors() {
        assertThat(guard.evaluate("把后缀为pdf的文件移动到资料目录"))
                .isEmpty();
        assertThat(guard.evaluate("把名称包含调测的文件夹移动到项目目录"))
                .isEmpty();
    }

    @Test
    void doesNotBlockCapabilityQuestionsExactPathsOrRenameValues() {
        assertThat(guard.evaluate("能按合同、发票这种业务类型归档吗？"))
                .isEmpty();
        assertThat(guard.evaluate("怎么删除空文件夹？"))
                .isEmpty();
        assertThat(guard.evaluate("把/云盘/会议纪要/周会.md归档到/云盘/会议纪要/2026下面"))
                .isEmpty();
        assertThat(guard.evaluate("把项目文档重命名为会议纪要-整理版.md"))
                .isEmpty();
    }
}
