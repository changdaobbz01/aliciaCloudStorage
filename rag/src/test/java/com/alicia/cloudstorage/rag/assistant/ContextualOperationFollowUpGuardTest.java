package com.alicia.cloudstorage.rag.assistant;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextualOperationFollowUpGuardTest {

    private final ContextualOperationFollowUpGuard guard = new ContextualOperationFollowUpGuard();

    @Test
    void resolvesCorrectionsAgainstTheActiveOperation() {
        assertDecision(
                "我说错了，不是合同，是报价",
                state("file_search", Map.of("target_name", "合同"), AssistantConversationFocus.empty()),
                "file_search",
                "CORRECTION",
                "SEARCH",
                "target_name",
                "报价"
        );
        assertDecision(
                "等等，新名字应该是周报-复核版.docx",
                state(
                        "file_rename",
                        Map.of("target_name", "周报.docx", "new_name", "周报-终稿.docx"),
                        AssistantConversationFocus.empty()
                ),
                "file_rename",
                "CORRECTION",
                "RENAME",
                "new_name",
                "周报-复核版.docx"
        );
        assertDecision(
                "名称改成季度复盘材料",
                state("folder_create", Map.of("new_folder_name", "季度材料"), AssistantConversationFocus.empty()),
                "folder_create",
                "CORRECTION",
                "CREATE_FOLDER",
                "new_folder_name",
                "季度复盘材料"
        );
        assertDecision(
                "目的地改成待复核资料目录",
                state(
                        "node_move",
                        Map.of("target_name", "法务意见.pdf", "target_folder", "归档区"),
                        AssistantConversationFocus.empty()
                ),
                "node_move",
                "CORRECTION",
                "MOVE",
                "target_folder",
                "待复核资料目录"
        );
    }

    @Test
    void explicitlyResumesASuspendedRenameButRejectsVaguePronouns() {
        AssistantConversationFocus suspended = new AssistantConversationFocus(
                "operation",
                "file_rename",
                "rename",
                Map.of("target_name", "我要", "new_name", "发布清单-确认版.xlsx"),
                null,
                null
        );
        AssistantConversationState interrupted = state("assistant_product_info", Map.of(), suspended);

        ContextualOperationFollowUpGuard.Decision resumed = guard
                .evaluate("回到刚才的重命名，源文件是发布清单.xlsx", interrupted)
                .orElseThrow();
        assertThat(resumed.intentId()).isEqualTo("file_rename");
        assertThat(resumed.relation()).isEqualTo("SLOT_FILL");
        assertThat(resumed.entities())
                .containsEntry("target_name", "发布清单.xlsx")
                .containsEntry("new_name", "发布清单-确认版.xlsx");

        assertThat(guard.evaluate("继续处理它", interrupted)).isEmpty();
        assertThat(guard.evaluate("就照刚才说的执行", interrupted)).isEmpty();
    }

    @Test
    void explicitlyReturnsToASuspendedSearchAndReplacesItsQuery() {
        AssistantConversationFocus suspended = new AssistantConversationFocus(
                "operation",
                "file_search",
                "search",
                Map.of("target_name", "测试", "scope", "current"),
                null,
                null
        );
        assertThat(suspended.hasResumableOperationContext()).isTrue();
        assertThat(suspended.hasOperationContext()).isFalse();
        AssistantConversationState interrupted = state("assistant_product_feedback", Map.of(), suspended);

        ContextualOperationFollowUpGuard.Decision resumed = guard
                .evaluate("回到搜索，把关键词改成验收，范围还是全部云盘", interrupted)
                .orElseThrow();

        assertThat(resumed.intentId()).isEqualTo("file_search");
        assertThat(resumed.operation()).isEqualTo("SEARCH");
        assertThat(resumed.entities())
                .containsEntry("target_name", "验收")
                .containsEntry("scope", "all");
    }

    @Test
    void narrowsCompoundAndMultiObjectRequestsWithoutLosingSafeContext() {
        ContextualOperationFollowUpGuard.Decision rename = guard.evaluate(
                "只重命名，不要删除",
                state(
                        "fallback",
                        Map.of("target_name", "周报草案.docx", "new_name", "周报终稿.docx"),
                        AssistantConversationFocus.empty()
                )
        ).orElseThrow();
        ContextualOperationFollowUpGuard.Decision move = guard.evaluate(
                "只移动方案甲.pdf",
                state(
                        "node_move",
                        Map.of("target_folder", "评审区"),
                        AssistantConversationFocus.empty()
                )
        ).orElseThrow();

        assertThat(rename.intentId()).isEqualTo("file_rename");
        assertThat(rename.operation()).isEqualTo("RENAME");
        assertThat(rename.entities())
                .containsEntry("target_name", "周报草案.docx")
                .containsEntry("new_name", "周报终稿.docx");
        assertThat(move.intentId()).isEqualTo("node_move");
        assertThat(move.operation()).isEqualTo("MOVE");
        assertThat(move.entities())
                .containsEntry("target_name", "方案甲.pdf")
                .containsEntry("target_folder", "评审区");
    }

    @Test
    void narrowsCompositeAndCollectionRequestsToOneExplicitAction() {
        AssistantConversationFocus compositeFocus = new AssistantConversationFocus(
                "operation",
                "folder_create_then_upload",
                "composite.create_folder_then_upload",
                Map.of("new_folder_name", "待发布"),
                null,
                null
        );
        ContextualOperationFollowUpGuard.Decision create = guard.evaluate(
                "取消上传，只新建待发布目录",
                state("folder_create_then_upload", Map.of("new_folder_name", "待发布"), compositeFocus)
        ).orElseThrow();
        ContextualOperationFollowUpGuard.Decision collection = guard.evaluate(
                "先只移动照片到家庭相册",
                state("fallback", Map.of(), AssistantConversationFocus.empty())
        ).orElseThrow();

        assertThat(create.intentId()).isEqualTo("folder_create");
        assertThat(create.operation()).isEqualTo("CREATE_FOLDER");
        assertThat(create.entities()).containsEntry("new_folder_name", "待发布");
        assertThat(collection.intentId()).isEqualTo("collection_move_by_category");
        assertThat(collection.operation()).isEqualTo("MOVE");
        assertThat(collection.entities())
                .containsEntry("file_type", "照片")
                .containsEntry("target_folder", "家庭相册");
    }

    private void assertDecision(
            String message,
            AssistantConversationState state,
            String intentId,
            String relation,
            String operation,
            String entity,
            String value
    ) {
        ContextualOperationFollowUpGuard.Decision decision = guard.evaluate(message, state).orElseThrow();
        assertThat(decision.intentId()).isEqualTo(intentId);
        assertThat(decision.relation()).isEqualTo(relation);
        assertThat(decision.operation()).isEqualTo(operation);
        assertThat(decision.entities()).containsEntry(entity, value);
    }

    private AssistantConversationState state(
            String intentId,
            Map<String, Object> entities,
            AssistantConversationFocus focus
    ) {
        return new AssistantConversationState(
                "conversation-8q",
                2,
                intentId,
                entities,
                List.of(),
                new ActionDraft("none", Map.of(), false),
                null,
                null,
                focus,
                SemanticFrame.empty(),
                "",
                Instant.now().plusSeconds(60)
        );
    }
}
