package com.alicia.cloudstorage.rag.assistant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompoundMutationScopeGuardTest {

    private final CompoundMutationScopeGuard guard = new CompoundMutationScopeGuard();

    @Test
    void blocksUnsupportedCompoundMutationsWithoutSelectingOneAction() {
        assertBlocked("把合同初稿.docx改名为合同定稿.docx，随后删除改名后的文件");
        assertBlocked("将会议材料.pdf搬到归档区，然后生成分享链接");
        assertBlocked("清理临时说明.txt，同时新建复核资料目录");
        assertBlocked("建立交付区，接着把云盘中的验收单.pdf移动进去");
        assertBlocked("新建复核区，然后把云盘里的校验单.pdf移进去");
    }

    @Test
    void preservesSameOperationIntentButRemovesPlanForMultipleExplicitFiles() {
        CompoundMutationScopeGuard.Decision move = guard
                .evaluate("把方案甲.pdf与方案乙.pdf移到评审资料目录")
                .orElseThrow();
        CompoundMutationScopeGuard.Decision delete = guard
                .evaluate("删除草稿A.docx以及草稿B.docx")
                .orElseThrow();
        CompoundMutationScopeGuard.Decision share = guard
                .evaluate("给“报价甲.pdf”和“报价乙.pdf”分别生成分享链接")
                .orElseThrow();

        assertThat(move.intentId()).isEqualTo("node_move");
        assertThat(move.operation()).isEqualTo("MOVE");
        assertThat(move.entities()).containsEntry("target_folder", "评审资料");
        assertThat(delete.intentId()).isEqualTo("file_delete");
        assertThat(delete.operation()).isEqualTo("DELETE");
        assertThat(share.intentId()).isEqualTo("file_share");
        assertThat(share.operation()).isEqualTo("SHARE");
    }

    @Test
    void blocksRepeatedMappingsAndIndependentCollectionMoves() {
        assertBlocked("把旧甲.pdf改为新甲.pdf，把旧乙.pdf改为新乙.pdf");
        assertBlocked("把照片移到相册，同时把视频移到媒体区");
        assertBlocked("删除第二个与第四个结果");
    }

    @Test
    void allowsOneActionQuotedLiteralsAndTheOnlySupportedComposite() {
        assertThat(guard.evaluate("创建名为“设计和开发”的文件夹")).isEmpty();
        assertThat(guard.evaluate("把“研发以及验收.docx”移到项目区")).isEmpty();
        assertThat(guard.evaluate("将“预算和决算.xlsx”改名为财务汇总.xlsx")).isEmpty();
        assertThat(guard.evaluate("搜索名称包含“移动并删除”的文件")).isEmpty();
        assertThat(guard.evaluate("找到发行说明.pdf并告诉我位置")).isEmpty();
        assertThat(guard.evaluate("先创建客户交付目录，再上传本地附件")).isEmpty();
        assertThat(guard.evaluate("上传到共享区")).isEmpty();
        assertThat(guard.evaluate("新建临时资料文件夹，名字改成临时交接")).isEmpty();
        assertThat(guard.evaluate("请将/云盘/共享空间/temp重命名成临时共享")).isEmpty();
        assertThat(guard.evaluate("把/云盘/共享空间/临时共享放进/云盘/归档/共享资料")).isEmpty();
        assertThat(guard.evaluate("把方案.pdf移到待审，等等，目标目录改成已审核")).isEmpty();
        assertThat(guard.evaluate("不要删除草稿.txt，改为把它移到待复核目录")).isEmpty();
        assertThat(guard.evaluate("改成把名称包含摘要的文件都移到轻量文档区")).isEmpty();
        assertThat(guard.evaluate("改为删除合同.pdf")).isEmpty();
        assertThat(guard.evaluate("把报告改成移动说明.pdf")).isEmpty();
    }

    @Test
    void ignoresCancelledMutationWhenOneAffirmativeActionRemains() {
        assertThat(guard.evaluate("先取消上传，只创建待发布目录")).isEmpty();
        assertThat(guard.evaluate("只改名，别删除")).isEmpty();
    }

    private void assertBlocked(String message) {
        CompoundMutationScopeGuard.Decision decision = guard.evaluate(message).orElseThrow();
        assertThat(decision.intentId()).as(message).isEqualTo("fallback");
        assertThat(decision.operation()).as(message).isEqualTo("UNKNOWN");
    }
}
