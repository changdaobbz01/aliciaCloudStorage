package com.alicia.cloudstorage.rag.assistant;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseOnlyConversationFollowUpGuardTest {

    private final ResponseOnlyConversationFollowUpGuard guard = new ResponseOnlyConversationFollowUpGuard();

    @Test
    void resolvesOnlyContextualResponseOnlyFollowUps() {
        assertDecision("那你叫什么名字", "assistant_user_identity", "assistant_identity");
        assertDecision("那你主要能协助我做哪些事情", "assistant_identity", "assistant_capability_examples");
        assertDecision("我今天有点忙，不过整体还好", "assistant_social", "assistant_chat");
        assertDecision("为什么这些功能值得使用", "assistant_product_info", "assistant_product_reason");
        assertDecision("尤其资料很多的时候会等得比较久", "assistant_product_feedback", "assistant_product_feedback");
        assertDecision("语气再委婉一些", "assistant_writing_help", "assistant_writing_help");
        assertDecision("再强调一下需要人工确认", "assistant_writing_help", "assistant_writing_help");
        assertDecision("那只推荐公开的官方渠道呢", "assistant_external_resource", "assistant_external_resource");
        assertDecision("告诉我订餐时通常要准备哪些信息", "assistant_errand_unsupported", "assistant_errand_unsupported");
        assertDecision("我先了解一下，不需要执行", "assistant_capability_examples", "assistant_acknowledgement");
        assertDecision("只给一个大概的诊断也行", "fallback", "fallback");
    }

    @Test
    void explicitFileCommandsAndUnrelatedContextAreNeverInherited() {
        assertThat(guard.evaluate("现在把会议记录.pdf移动到归档目录", state("assistant_writing_help"))).isEmpty();
        assertThat(guard.evaluate("搜索季度复盘.pdf", state("assistant_product_info"))).isEmpty();
        assertThat(guard.evaluate("语气再委婉一些", state("assistant_chat"))).isEmpty();
        assertThat(guard.evaluate("不保证准确也可以，你给个结论吧", state("assistant_product_info"))).isEmpty();
        assertThat(guard.evaluate("语气再委婉一些", null)).isEmpty();
    }

    private void assertDecision(String message, String previousIntent, String expectedIntent) {
        ResponseOnlyConversationFollowUpGuard.Decision decision = guard
                .evaluate(message, state(previousIntent))
                .orElseThrow();
        assertThat(decision.intentId()).isEqualTo(expectedIntent);
        assertThat(decision.relation()).isEqualTo("FOLLOW_UP");
        assertThat(decision.operation()).isEqualTo("RESPOND");
    }

    private AssistantConversationState state(String intentId) {
        return new AssistantConversationState(
                "conversation-8p",
                1,
                intentId,
                Map.of(),
                List.of(),
                new ActionDraft("none", Map.of(), false),
                null,
                null,
                AssistantConversationFocus.empty(),
                SemanticFrame.empty(),
                "",
                Instant.now().plusSeconds(60)
        );
    }
}
