package com.alicia.cloudstorage.rag.assistant;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NonExecutableRequestGuardTest {

    private final NonExecutableRequestGuard guard = new NonExecutableRequestGuard();

    @Test
    void detectsLanguageThatMustNotBecomeAnExecutionPlan() {
        assertThat(guard.evaluate("先不要删除合同.pdf", null).orElseThrow().id())
                .isEqualTo("negated_operation");
        assertThat(guard.evaluate("如果删除合同.pdf会发生什么", null).orElseThrow().id())
                .isEqualTo("hypothetical_operation");
        assertThat(guard.evaluate("“删除合同.pdf”这句话是什么意思", null).orElseThrow().id())
                .isEqualTo("quoted_operation_explanation");
        assertThat(guard.evaluate("把合同.pdf删除或者移动到资料目录", null).orElseThrow().id())
                .isEqualTo("conflicting_operations");
        assertThat(guard.evaluate("把它删掉", null).orElseThrow().id())
                .isEqualTo("unresolved_reference");
    }

    @Test
    void cancellationRequiresPendingOperationContext() {
        assertThat(guard.evaluate("算了，不用了", null)).isEmpty();

        AssistantConversationState pending = new AssistantConversationState(
                "conversation-1",
                1,
                "folder_create",
                Map.of("new_folder_name", "待取消"),
                List.of(),
                new ActionDraft("folder.create", Map.of("folderName", "待取消"), false),
                null,
                null,
                AssistantConversationFocus.empty(),
                SemanticFrame.empty(),
                "",
                Instant.now().plusSeconds(60)
        );

        NonExecutableRequestGuard.Decision decision = guard
                .evaluate("算了，不用了", pending)
                .orElseThrow();
        assertThat(decision.id()).isEqualTo("cancel_pending_operation");
        assertThat(decision.relation()).isEqualTo("CANCELLATION");
    }

    @Test
    void doesNotBlockAffirmativeOrConditionalCommands() {
        assertThat(guard.evaluate("不要忘记删除合同.pdf", null)).isEmpty();
        assertThat(guard.evaluate("如果目录存在就创建归档文件夹", null)).isEmpty();
        assertThat(guard.evaluate("创建归档文件夹，然后上传文件", null)).isEmpty();
        assertThat(guard.evaluate("把这个文件上传到项目资料", null)).isEmpty();
    }
}
