package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantControllerTest {

    @Test
    void exposesActionBridgeContractFromConfig() {
        AssistantController controller = new AssistantController(
                null,
                new RagConfigLoader(new ObjectMapper()),
                new ObjectMapper().findAndRegisterModules()
        );

        Map<String, Object> contract = controller.actionBridgeContract();
        Map<?, ?> actions = (Map<?, ?>) contract.get("actions");
        List<String> actionKeys = actions.keySet().stream().map(String::valueOf).toList();

        assertThat(contract).containsEntry("version", "action_bridge_v2");
        assertThat(actionKeys).contains(
                "rename",
                "delete",
                "share",
                "folder.create",
                "upload_target",
                "collection.move_by_category",
                "collection.rename_add_prefix"
        );
    }

    @Test
    void exposesMobileContractAndAcceptanceScenariosFromConfig() {
        AssistantController controller = new AssistantController(
                null,
                new RagConfigLoader(new ObjectMapper()),
                new ObjectMapper().findAndRegisterModules()
        );

        Map<String, Object> mobile = controller.mobileContract();
        Map<String, Object> acceptance = controller.acceptanceScenarios();

        assertThat(mobile).containsEntry("version", "mobile_contract_v1");
        assertThat(map(mobile.get("actionHandlers"))).containsKeys(
                "none",
                "search",
                "rename",
                "delete",
                "share",
                "folder.create",
                "upload_target",
                "composite.create_folder_then_upload"
        );
        assertThat(acceptance).containsEntry("version", "rag_mobile_acceptance_v1");
        assertThat((List<?>) acceptance.get("automatedScenarios")).isNotEmpty();
        assertThat((List<?>) acceptance.get("manualScenarios")).isNotEmpty();
    }

    @Test
    void exposesActionPlanContractFromConfig() {
        AssistantController controller = new AssistantController(
                null,
                new RagConfigLoader(new ObjectMapper()),
                new ObjectMapper().findAndRegisterModules()
        );

        Map<String, Object> contract = controller.actionPlanContract();

        assertThat(contract).containsKeys(
                "schema",
                "actions",
                "composites",
                "collections",
                "policies",
                "semanticArbitration",
                "assistantResponsePolicy",
                "dialogue",
                "persona",
                "mobile",
                "acceptanceScenarios"
        );
        assertThat(map(contract.get("schema"))).containsEntry("version", "action_plan_v2");
        assertThat(map(contract.get("actions"))).containsEntry("version", "action_templates_v1");
        assertThat(map(contract.get("composites"))).containsEntry("version", "composite_actions_v1");
        assertThat(map(contract.get("collections"))).containsEntry("version", "collection_actions_v1");
        assertThat(map(contract.get("semanticArbitration"))).containsEntry("version", "semantic_arbitration_v1");
        assertThat(map(contract.get("assistantResponsePolicy"))).containsEntry("version", "assistant_response_policy_v1");
        assertThat(map(contract.get("persona"))).containsEntry("id", "anan");
        assertThat(map(contract.get("mobile"))).containsEntry("version", "mobile_contract_v1");
        assertThat(map(contract.get("acceptanceScenarios"))).containsEntry("version", "rag_mobile_acceptance_v1");
    }

    @Test
    void deepSeekConfigUsesEnvironmentVariableNameOnly() {
        Map<String, Object> config = new RagConfigLoader(new ObjectMapper())
                .loadJsonMap("rag/llm/deepseek.json");

        assertThat(config).containsEntry("enabled", true);
        assertThat(config).containsEntry("api_key_env", "DEEPSEEK_API_KEY");
        assertThat(config).doesNotContainKey("api_key");
        assertThat(map(config.get("prompt")).get("system_message").toString())
                .contains("single semantic understanding layer");
        assertThat(map(config.get("prompt"))).containsEntry("version", "semantic_frame_v2");
        assertThat(map(config.get("prompt")).get("user_template").toString())
                .contains("{retrieval_examples_json}");
    }

    @Test
    void exposesVersionedCapabilityRegistry() {
        AssistantController controller = new AssistantController(
                null,
                new RagConfigLoader(new ObjectMapper()),
                new ObjectMapper().findAndRegisterModules()
        );

        Map<String, Object> capabilities = controller.capabilities();

        assertThat(capabilities)
                .containsEntry("protocolVersion", "assistant_protocol_v2")
                .containsEntry("actionBridgeVersion", "action_bridge_v2");
        assertThat((List<?>) capabilities.get("operations"))
                .extracting(String::valueOf)
                .contains("NAVIGATE", "OPEN_FILE");
    }

    @Test
    void streamStatusTextDoesNotExposeDebugIntentWording() throws Exception {
        AssistantPlanStreamService streamService = new AssistantPlanStreamService(null, 3_000L);

        String status = streamService.statusText(responseWithNextAction("安安身份介绍", "respond_only"));

        assertThat(status)
                .doesNotContain("识别为")
                .doesNotContain("意图")
                .isEqualTo("安安正在整理回复...");
    }

    @Test
    void preservesStructuredClientEventWhenSanitizingPlanRequest() {
        AssistantConversationService service = mock(AssistantConversationService.class);
        AssistantController controller = new AssistantController(
                service,
                new RagConfigLoader(new ObjectMapper()),
                new ObjectMapper().findAndRegisterModules()
        );
        AssistantClientEvent event = new AssistantClientEvent("SELECT_CANDIDATE", 501L, 1);
        AssistantClientContext context = new AssistantClientContext(null, "/", Map.of("files", 1));

        controller.plan(new AssistantPlanRequest("  选择第1个  ", "conversation-1", context, event), "Bearer token");

        verify(service).plan(
                eq(new AssistantPlanRequest("选择第1个", "conversation-1", context, event)),
                eq("Bearer token")
        );
    }

    @Test
    void streamSendsHeartbeatWhilePlanIsStillRunning() throws Exception {
        AssistantConversationService service = mock(AssistantConversationService.class);
        AssistantPlanRequest request = new AssistantPlanRequest("删除第一个文件", "conversation-1");
        when(service.plan(eq(request), eq("Bearer token"))).thenAnswer(invocation -> {
            Thread.sleep(600L);
            return responseWithNextAction("文件删除", "wait_for_user_confirmation");
        });
        AssistantPlanStreamService streamService = new AssistantPlanStreamService(service, 250L);
        CapturingSseEmitter emitter = new CapturingSseEmitter();

        streamService.stream(request, "Bearer token", emitter);

        assertThat(emitter.awaitCompletion()).isTrue();
        assertThat(emitter.payload())
                .contains("安安还在结合上下文认真理解")
                .contains("status")
                .contains("final")
                .contains("done");
    }

    @Test
    void streamCancelsSlowPlanAndReturnsControlledError() throws Exception {
        AssistantConversationService service = mock(AssistantConversationService.class);
        AssistantPlanRequest request = new AssistantPlanRequest("打开文件记录文件夹", "conversation-1");
        when(service.plan(eq(request), eq("Bearer token"))).thenAnswer(invocation -> {
            Thread.sleep(5_000L);
            return responseWithNextAction("打开文件夹", "show_search_results");
        });
        AssistantPlanStreamService streamService = new AssistantPlanStreamService(service, 25L, 1_000L, 120L);
        CapturingSseEmitter emitter = new CapturingSseEmitter();

        streamService.stream(request, "Bearer token", emitter);

        assertThat(emitter.awaitCompletion()).isTrue();
        assertThat(emitter.payload())
                .contains("error")
                .contains("超过了安全等待时间")
                .contains("done");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object source) {
        return (Map<String, Object>) source;
    }

    private static IntentRecognitionResponse responseWithNextAction(String intentName, String nextAction) {
        return new IntentRecognitionResponse(
                "test",
                "test",
                "test",
                "test",
                "test",
                "",
                "assistant_identity",
                intentName,
                "assistant_persona",
                1.0,
                "",
                "",
                Map.of(),
                List.of(),
                List.of(),
                nextAction,
                null,
                null,
                null,
                null,
                "",
                "",
                "",
                "",
                null,
                null
        );
    }

    private static final class CapturingSseEmitter extends SseEmitter {
        private final List<String> values = new ArrayList<>();
        private final CountDownLatch completed = new CountDownLatch(1);

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            builder.build().forEach(item -> values.add(String.valueOf(item.getData())));
        }

        @Override
        public synchronized void complete() {
            completed.countDown();
        }

        @Override
        public synchronized void completeWithError(Throwable error) {
            values.add("error:" + error.getMessage());
            completed.countDown();
        }

        boolean awaitCompletion() throws InterruptedException {
            return completed.await(2, TimeUnit.SECONDS);
        }

        String payload() {
            return String.join("\n", values);
        }
    }
}
