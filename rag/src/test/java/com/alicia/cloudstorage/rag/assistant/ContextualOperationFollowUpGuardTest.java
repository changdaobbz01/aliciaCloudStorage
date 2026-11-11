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
