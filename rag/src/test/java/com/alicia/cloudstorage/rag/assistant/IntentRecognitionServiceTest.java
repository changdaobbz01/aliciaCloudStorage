package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IntentRecognitionServiceTest {

    private RagConfigLoader configLoader;
    private IntentRouter intentRouter;

    @BeforeEach
    void setUp() {
        configLoader = new RagConfigLoader(new ObjectMapper());
        intentRouter = new IntentRouter(configLoader, new EntityExtractor(configLoader));
    }

    @Test
    void generatedPlanningReplyCanBePolishedWithoutChangingStructuredResponse() {
        AssistantReplyPolisher polisher = request -> Optional.of("我会先核对操作范围，等你确认预览后再继续删除。");
        IntentRecognitionService service = new IntentRecognitionService(
                message -> Optional.empty(),
                intentRouter,
                configLoader,
                polisher
        );
        IntentRecognitionResponse response = service.recognizeLocal(
                "删除测试目录里的全部文件",
                "test"
        );

        IntentRecognitionResponse polished = service.polishGeneratedReply(
                "删除测试目录里的全部文件",
                response.withAssistantText("我已经核对操作范围，请确认后再继续。")
        );

        assertThat(polished.assistantText()).isEqualTo("我会先核对操作范围，等你确认预览后再继续删除。");
        assertThat(polished.actionDraft()).isEqualTo(response.actionDraft());
        assertThat(polished.entities()).isEqualTo(response.entities());
    }

    @Test
    void generatedPlanningReplyRejectsUnsafeExecutionClaim() {
        AssistantReplyPolisher polisher = request -> Optional.of("文件已经全部删除完成。");
        IntentRecognitionService service = new IntentRecognitionService(
                message -> Optional.empty(),
                intentRouter,
                configLoader,
                polisher
        );
        IntentRecognitionResponse response = service.recognizeLocal(
                "删除测试目录里的全部文件",
                "test"
        ).withAssistantText("请确认预览后再继续删除。");

        IntentRecognitionResponse polished = service.polishGeneratedReply(
                "删除测试目录里的全部文件",
                response
        );

        assertThat(polished.assistantText()).isEqualTo("请确认预览后再继续删除。");
    }

    @Test
    void returnsDeepSeekIntentTemplateWithoutMockCandidates() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_file_intent_recognition",
                "intent_recognition_v1",
                Map.ofEntries(
                        Map.entry("intent_id", "file_rename"),
                        Map.entry("intent_name", "文件重命名"),
                        Map.entry("task_type", "file_mutation"),
                        Map.entry("confidence", 0.91),
                        Map.entry("user_goal", "把项目计划改成最终版"),
                        Map.entry("normalized_query", "项目计划"),
                        Map.entry("entities", Map.of("target_name", "项目计划", "new_name", "项目计划-最终版.docx")),
                        Map.entry("required_slots", List.of("target_name", "new_name")),
                        Map.entry("missing_slots", List.of()),
                        Map.entry("next_action", "wait_for_backend_binding"),
                        Map.entry("risk", "medium"),
                        Map.entry("requires_confirmation", true),
                        Map.entry("action_draft", Map.of(
                                "type", "rename",
                                "parameters", Map.of("target_name", "项目计划", "new_name", "项目计划-最终版.docx"),
                                "needs_backend_binding", true
                        )),
                        Map.entry("assistant_text", "已识别为重命名需求，等待后端匹配真实文件。"),
                        Map.entry("clarification_question", ""),
                        Map.entry("reason", "用户明确表达重命名。")
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("把项目计划重命名为 项目计划-最终版.docx");

        assertThat(response.provider()).isEqualTo("deepseek");
        assertThat(response.intentId()).isEqualTo("file_rename");
        assertThat(response.actionDraft().type()).isEqualTo("rename");
        assertThat(response.actionDraft().needsBackendBinding()).isTrue();
        assertThat(response.safety().allowedToExecute()).isFalse();
        assertThat(response.entities()).containsEntry("new_name", "项目计划-最终版.docx");
    }

    @Test
    void preservesDeepSeekSemanticDirectoryQueryPlan() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_file_intent_recognition",
                "intent_recognition_v3",
                Map.ofEntries(
                        Map.entry("intent_id", "file_search"),
                        Map.entry("intent_name", "文件检索"),
                        Map.entry("task_type", "file_query"),
                        Map.entry("confidence", 0.98),
                        Map.entry("normalized_query", "列出根目录文件夹"),
                        Map.entry("entities", Map.of(
                                "query_mode", "directory_list",
                                "scope", "root",
                                "result_type", "FOLDER"
                        )),
                        Map.entry("missing_slots", List.of()),
                        Map.entry("next_action", "wait_for_backend_binding"),
                        Map.entry("action_draft", Map.of(
                                "type", "search",
                                "parameters", Map.of(
                                        "query_mode", "directory_list",
                                        "scope", "root",
                                        "result_type", "FOLDER"
                                ),
                                "needs_backend_binding", true
                        )),
                        Map.entry("assistant_text", "我来列出根目录下的文件夹。")
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("列出根目录文件夹列表");

        assertThat(response.provider()).isEqualTo("deepseek");
        assertThat(response.intentId()).isEqualTo("file_search");
        assertThat(response.entities())
                .containsEntry("query_mode", "directory_list")
                .containsEntry("scope", "root")
                .containsEntry("result_type", "FOLDER")
                .doesNotContainKey("target_name");
    }

    @Test
    void fallsBackToConfiguredRulesWhenModelUnavailable() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("删除临时截图");

        assertThat(response.provider()).isEqualTo("local_fallback");
        assertThat(response.intentId()).isEqualTo("file_delete");
        assertThat(response.safety().risk()).isEqualTo("high");
        assertThat(response.actionDraft().type()).isEqualTo("delete");
        assertThat(response.actionDraft().needsBackendBinding()).isTrue();
    }

    @Test
    void highConfidenceConfiguredIntentGuardsAgainstStochasticModelFallback() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_semantic_frame_v2",
                "semantic_frame_v2",
                Map.of(
                        "intent_id", "fallback",
                        "confidence", 0.2,
                        "entities", Map.of(),
                        "assistant_text", "我没有理解你的意思。"
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("你是谁");

        assertThat(response.provider()).isEqualTo("local_fallback");
        assertThat(response.intentId()).isEqualTo("assistant_identity");
        assertThat(response.semanticFrame().operation()).isEqualTo("RESPOND");
        assertThat(response.assistantText()).contains("安安").doesNotContain("没有理解");
    }

    @Test
    void configuredIntentContractOverridesModelNextAction() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_semantic_frame_v2",
                "semantic_frame_v2",
                Map.of(
                        "intent_id", "assistant_capability_examples",
                        "confidence", 0.98,
                        "entities", Map.of(),
                        "next_action", "wait_for_backend_binding",
                        "assistant_text", "我可以帮你管理云盘文件。"
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("你能做什么");

        assertThat(response.intentId()).isEqualTo("assistant_capability_examples");
        assertThat(response.nextAction()).isEqualTo("respond_only");
    }

    @Test
    void localFallbackUsesConfiguredOutOfScopeBoundaryText() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("今天的风有点大");

        assertThat(response.provider()).isEqualTo("local_fallback");
        assertThat(response.intentId()).isEqualTo("fallback");
        assertThat(response.assistantText()).contains("再明确一点");
        assertThat(response.semanticFrame().ambiguities()).contains("operation");
        assertThat(response.semanticFrame().clarification().suggestions()).isNotEmpty();
    }

    @Test
    void localFallbackSatisfiesMobileAcceptanceScenarios() {
        IntentModelClient unavailableClient = message -> Optional.empty();
        IntentRecognitionService service = new IntentRecognitionService(unavailableClient, intentRouter, configLoader);
        Map<String, Object> scenarios = configLoader.loadJsonMap("rag/conversation/acceptance_scenarios.json");

        for (Object item : list(scenarios, "automatedScenarios")) {
            Map<String, Object> scenario = map(item);
            Map<String, Object> expected = map(scenario, "expected");
            IntentRecognitionResponse response = service.recognize(String.valueOf(scenario.get("message")));

            assertThat(response.intentId())
                    .as("scenario %s intentId", scenario.get("id"))
                    .isEqualTo(expected.get("intentId"));
            assertThat(response.nextAction())
                    .as("scenario %s nextAction", scenario.get("id"))
                    .isEqualTo(expected.get("nextAction"));
            assertThat(response.actionDraft().type())
                    .as("scenario %s actionDraftType", scenario.get("id"))
                    .isEqualTo(expected.get("actionDraftType"));
            assertThat(response.safety().risk())
                    .as("scenario %s risk", scenario.get("id"))
                    .isEqualTo(expected.get("risk"));
            assertThat(response.safety().requiresConfirmation())
                    .as("scenario %s requiresConfirmation", scenario.get("id"))
                    .isEqualTo(expected.get("requiresConfirmation"));
        }
    }

    @Test
    void modelFallbackUsesConfiguredTemplateInsteadOfGeneratedText() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_file_intent_recognition",
                "intent_recognition_v1",
                Map.ofEntries(
                        Map.entry("intent_id", "fallback"),
                        Map.entry("intent_name", "兜底澄清"),
                        Map.entry("task_type", "fallback"),
                        Map.entry("confidence", 0.4),
                        Map.entry("user_goal", "闲聊天气"),
                        Map.entry("normalized_query", ""),
                        Map.entry("entities", Map.of()),
                        Map.entry("required_slots", List.of("operation")),
                        Map.entry("missing_slots", List.of("operation")),
                        Map.entry("next_action", "ask_clarification"),
                        Map.entry("risk", "none"),
                        Map.entry("requires_confirmation", false),
                        Map.entry("action_draft", Map.of(
                                "type", "none",
                                "parameters", Map.of(),
                                "needs_backend_binding", false
                        )),
                        Map.entry("assistant_text", "识别为兜底澄清，请补充要执行的操作。"),
                        Map.entry("clarification_question", ""),
                        Map.entry("reason", "模型认为需要兜底。")
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("今天的风有点大");

        assertThat(response.provider()).isEqualTo("deepseek");
        assertThat(response.intentId()).isEqualTo("fallback");
        assertThat(response.assistantText())
                .contains("再明确一点")
                .doesNotContain("识别为")
                .doesNotContain("请补充要执行的操作");
    }

    @Test
    void modelFallbackUsesSafeGeneratedTextWhenAvailable() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_file_intent_recognition",
                "intent_recognition_v1",
                Map.ofEntries(
                        Map.entry("intent_id", "fallback"),
                        Map.entry("intent_name", "兜底澄清"),
                        Map.entry("task_type", "fallback"),
                        Map.entry("confidence", 0.4),
                        Map.entry("user_goal", "普通闲聊"),
                        Map.entry("normalized_query", message),
                        Map.entry("entities", Map.of()),
                        Map.entry("required_slots", List.of()),
                        Map.entry("missing_slots", List.of()),
                        Map.entry("next_action", "ask_clarification"),
                        Map.entry("risk", "none"),
                        Map.entry("requires_confirmation", false),
                        Map.entry("action_draft", Map.of(
                                "type", "none",
                                "parameters", Map.of(),
                                "needs_backend_binding", false
                        )),
                        Map.entry("assistant_text", "听起来你只是随口感叹一下。安安在这儿，需要继续聊或者整理云盘文件，都可以直接说。"),
                        Map.entry("clarification_question", ""),
                        Map.entry("reason", "模型未匹配受控文件意图。")
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("今天的风有点大");

        assertThat(response.provider()).isEqualTo("deepseek");
        assertThat(response.intentId()).isEqualTo("fallback");
        assertThat(response.assistantText())
                .contains("再明确一点")
                .doesNotContain("识别为");
    }

    @Test
    void highConfidenceAcknowledgementOverridesModelFallbackAndKeepsNaturalReply() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_file_intent_recognition",
                "intent_recognition_v1",
                Map.ofEntries(
                        Map.entry("intent_id", "fallback"),
                        Map.entry("intent_name", "兜底澄清"),
                        Map.entry("task_type", "fallback"),
                        Map.entry("confidence", 0.4),
                        Map.entry("user_goal", "普通闲聊"),
                        Map.entry("normalized_query", message),
                        Map.entry("entities", Map.of()),
                        Map.entry("required_slots", List.of()),
                        Map.entry("missing_slots", List.of()),
                        Map.entry("next_action", "ask_clarification"),
                        Map.entry("risk", "none"),
                        Map.entry("requires_confirmation", false),
                        Map.entry("action_draft", Map.of(
                                "type", "none",
                                "parameters", Map.of(),
                                "needs_backend_binding", false
                        )),
                        Map.entry("assistant_text", "识别为兜底澄清。"),
                        Map.entry("clarification_question", ""),
                        Map.entry("reason", "模型未匹配受控文件意图。")
                )
        ));
        AtomicInteger polishCalls = new AtomicInteger();
        AssistantReplyPolisher polisher = request -> {
            polishCalls.incrementAndGet();
            return Optional.of("嗯，我听到啦。想继续聊也可以；要整理云盘文件时，直接告诉我目标就好。");
        };

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader, polisher)
                .recognize("好吧，今天先这样");

        assertThat(response.provider()).isEqualTo("local_fallback");
        assertThat(response.intentId()).isEqualTo("assistant_acknowledgement");
        assertThat(response.assistantText())
                .contains("好呀")
                .doesNotContain("识别为");
        assertThat(polishCalls).hasValue(0);
    }

    @Test
    void unavailableModelUsesConfiguredTextWithoutRetryingReplyPolisher() {
        IntentModelClient unavailableClient = message -> Optional.empty();
        AtomicInteger polishCalls = new AtomicInteger();
        AssistantReplyPolisher polisher = request -> {
            polishCalls.incrementAndGet();
            return Optional.of("已删除临时截图，处理好了。");
        };

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader, polisher)
                .recognize("删除临时截图");

        assertThat(response.intentId()).isEqualTo("file_delete");
        assertThat(response.assistantText())
                .doesNotContain("已删除")
                .contains("移入回收站")
                .contains("删除计划");
        assertThat(polishCalls).hasValue(0);
    }

    @Test
    void safeModelReplyIsReusedWithoutASecondPolishingCall() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_file_intent_recognition",
                "intent_recognition_v1",
                Map.ofEntries(
                        Map.entry("intent_id", "assistant_identity"),
                        Map.entry("intent_name", "助手身份咨询"),
                        Map.entry("task_type", "assistant_conversation"),
                        Map.entry("confidence", 0.96),
                        Map.entry("user_goal", "询问安安能力"),
                        Map.entry("normalized_query", message),
                        Map.entry("entities", Map.of()),
                        Map.entry("required_slots", List.of()),
                        Map.entry("missing_slots", List.of()),
                        Map.entry("next_action", "respond_only"),
                        Map.entry("risk", "none"),
                        Map.entry("requires_confirmation", false),
                        Map.entry("action_draft", Map.of(
                                "type", "none",
                                "parameters", Map.of(),
                                "needs_backend_binding", false
                        )),
                        Map.entry("assistant_text", "我是安安，可以帮你查找、整理和分享云盘里的文件。"),
                        Map.entry("clarification_question", ""),
                        Map.entry("reason", "用户在询问助手能力。")
                )
        ));
        AtomicInteger polishCalls = new AtomicInteger();
        AssistantReplyPolisher polisher = request -> {
            polishCalls.incrementAndGet();
            return Optional.of("不应调用");
        };

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader, polisher)
                .recognize("你能做什么？");

        assertThat(response.assistantText()).isEqualTo("我是安安，可以帮你查找、整理和分享云盘里的文件。");
        assertThat(polishCalls).hasValue(0);
    }

    @Test
    void modelRespondOnlyUsesConfiguredPersonaTemplateWhenPreferred() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-chat",
                "deepseek_file_intent_recognition",
                "intent_recognition_v1",
                Map.ofEntries(
                        Map.entry("intent_id", "assistant_identity"),
                        Map.entry("intent_name", "助手身份咨询"),
                        Map.entry("task_type", "assistant_conversation"),
                        Map.entry("confidence", 0.95),
                        Map.entry("user_goal", "询问安安能力"),
                        Map.entry("normalized_query", message),
                        Map.entry("entities", Map.of()),
                        Map.entry("required_slots", List.of()),
                        Map.entry("missing_slots", List.of()),
                        Map.entry("next_action", "respond_only"),
                        Map.entry("risk", "none"),
                        Map.entry("requires_confirmation", false),
                        Map.entry("action_draft", Map.of(
                                "type", "none",
                                "parameters", Map.of(),
                                "needs_backend_binding", false
                        )),
                        Map.entry("assistant_text", "识别为助手身份咨询，将介绍我的能力范围。"),
                        Map.entry("clarification_question", ""),
                        Map.entry("reason", "模型识别为助手身份咨询。")
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("你的能力是什么呢？可以为我做什么呢？");

        assertThat(response.provider()).isEqualTo("deepseek");
        assertThat(response.intentId()).isEqualTo("assistant_identity");
        assertThat(response.assistantText())
                .contains("Alicia")
                .contains("安安")
                .doesNotContain("识别为助手身份咨询");
    }

    @Test
    void modelCapabilityExamplesUsesConfiguredPersonaTemplateWhenPreferred() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-chat",
                "deepseek_file_intent_recognition",
                "intent_recognition_v1",
                Map.ofEntries(
                        Map.entry("intent_id", "assistant_capability_examples"),
                        Map.entry("intent_name", "安安能力举例"),
                        Map.entry("task_type", "assistant_persona"),
                        Map.entry("confidence", 0.95),
                        Map.entry("user_goal", "要求举例说明安安能力"),
                        Map.entry("normalized_query", message),
                        Map.entry("entities", Map.of()),
                        Map.entry("required_slots", List.of()),
                        Map.entry("missing_slots", List.of()),
                        Map.entry("next_action", "respond_only"),
                        Map.entry("risk", "none"),
                        Map.entry("requires_confirmation", false),
                        Map.entry("action_draft", Map.of(
                                "type", "none",
                                "parameters", Map.of(),
                                "needs_backend_binding", false
                        )),
                        Map.entry("assistant_text", "识别为能力举例咨询，将介绍能力范围。"),
                        Map.entry("clarification_question", ""),
                        Map.entry("reason", "模型识别为能力举例。")
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("详细举例，看看你的能力项");

        assertThat(response.provider()).isEqualTo("deepseek");
        assertThat(response.intentId()).isEqualTo("assistant_capability_examples");
        assertThat(response.assistantText())
                .contains("比如")
                .contains("确认")
                .doesNotContain("识别为能力举例咨询");
    }

    @Test
    void normalizesUnsafeModelOutputToConfiguredContract() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_file_intent_recognition",
                "intent_recognition_v1",
                Map.ofEntries(
                        Map.entry("intent_id", "file_delete"),
                        Map.entry("intent_name", "文件删除"),
                        Map.entry("task_type", "file_mutation"),
                        Map.entry("confidence", 1.7),
                        Map.entry("user_goal", "删除临时截图"),
                        Map.entry("normalized_query", "临时截图"),
                        Map.entry("entities", Map.of(
                                "target_name", "临时截图",
                                "nodeId", 12,
                                "ownerId", 7
                        )),
                        Map.entry("required_slots", List.of()),
                        Map.entry("missing_slots", List.of()),
                        Map.entry("next_action", "execute_action"),
                        Map.entry("risk", "none"),
                        Map.entry("requires_confirmation", false),
                        Map.entry("action_draft", Map.of(
                                "type", "download",
                                "parameters", Map.of(
                                        "target_name", "临时截图",
                                        "nodeId", 12,
                                        "storagePath", "cos/private/path.png"
                                ),
                                "needs_backend_binding", false
                        )),
                        Map.entry("assistant_text", "已删除临时截图。"),
                        Map.entry("clarification_question", ""),
                        Map.entry("reason", "模型输出包含不可信执行信息。")
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("删除临时截图");

        assertThat(response.confidence()).isEqualTo(1.0);
        assertThat(response.intentId()).isEqualTo("file_delete");
        assertThat(response.nextAction()).isEqualTo("wait_for_backend_binding");
        assertThat(response.safety().risk()).isEqualTo("high");
        assertThat(response.safety().requiresConfirmation()).isTrue();
        assertThat(response.actionDraft().type()).isEqualTo("delete");
        assertThat(response.actionDraft().needsBackendBinding()).isTrue();
        assertThat(response.entities()).containsEntry("target_name", "临时截图");
        assertThat(response.entities()).doesNotContainKeys("nodeId", "ownerId");
        assertThat(response.actionDraft().parameters()).doesNotContainKeys("nodeId", "storagePath");
    }

    @Test
    void ignoresOptionalShareRecipientClarification() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_file_intent_recognition",
                "intent_recognition_v1",
                Map.ofEntries(
                        Map.entry("intent_id", "file_share"),
                        Map.entry("intent_name", "文件分享"),
                        Map.entry("task_type", "file_mutation"),
                        Map.entry("confidence", 0.7),
                        Map.entry("user_goal", "分享最近的预算表"),
                        Map.entry("normalized_query", "最近的预算表"),
                        Map.entry("entities", Map.of("target_name", "最近的预算表")),
                        Map.entry("required_slots", List.of("target_name", "share_scope")),
                        Map.entry("missing_slots", List.of("share_scope")),
                        Map.entry("next_action", "ask_clarification"),
                        Map.entry("risk", "medium"),
                        Map.entry("requires_confirmation", true),
                        Map.entry("action_draft", Map.of(
                                "type", "share",
                                "parameters", Map.of("target_name", "最近的预算表"),
                                "needs_backend_binding", true
                        )),
                        Map.entry("assistant_text", "请说明要分享给谁。"),
                        Map.entry("clarification_question", "要分享给谁？"),
                        Map.entry("reason", "模型误把收件人当必填。")
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("分享最近的预算表");

        assertThat(response.intentId()).isEqualTo("file_share");
        assertThat(response.missingSlots()).isEmpty();
        assertThat(response.nextAction()).isEqualTo("wait_for_backend_binding");
        assertThat(response.clarificationQuestion()).isBlank();
        assertThat(response.assistantText()).doesNotContain("分享给谁");
    }

    @Test
    void uploadTargetsCloudFolderInsteadOfLocalFilePath() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_file_intent_recognition",
                "intent_recognition_v1",
                Map.ofEntries(
                        Map.entry("intent_id", "file_upload"),
                        Map.entry("intent_name", "上传目标定位"),
                        Map.entry("task_type", "file_mutation"),
                        Map.entry("confidence", 0.85),
                        Map.entry("user_goal", "上传到项目资料"),
                        Map.entry("normalized_query", "项目资料"),
                        Map.entry("entities", Map.of("target_folder", "项目资料")),
                        Map.entry("required_slots", List.of("target_folder", "file_path")),
                        Map.entry("missing_slots", List.of("file_path")),
                        Map.entry("next_action", "ask_clarification"),
                        Map.entry("risk", "low"),
                        Map.entry("requires_confirmation", true),
                        Map.entry("action_draft", Map.of(
                                "type", "upload_target",
                                "parameters", Map.of("target_folder", "项目资料", "file_path", "C:/secret.txt"),
                                "needs_backend_binding", true
                        )),
                        Map.entry("assistant_text", "请提供本地文件路径。"),
                        Map.entry("clarification_question", "要上传哪个本地文件？"),
                        Map.entry("reason", "模型误把本地路径当必填。")
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("上传到项目资料");

        assertThat(response.intentId()).isEqualTo("file_upload");
        assertThat(response.entities()).containsEntry("target_folder", "项目资料");
        assertThat(response.missingSlots()).isEmpty();
        assertThat(response.nextAction()).isEqualTo("wait_for_backend_binding");
        assertThat(response.actionDraft().parameters()).doesNotContainKey("file_path");
    }

    @Test
    void localFallbackAsksForDeleteTargetWhenMissing() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("删除");

        assertThat(response.provider()).isEqualTo("local_fallback");
        assertThat(response.intentId()).isEqualTo("file_delete");
        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.missingSlots()).containsExactly("target_name");
        assertThat(response.clarificationQuestion()).contains("要删除");
    }

    @Test
    void localFallbackRecognizesNameContainsListAsSearch() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("帮我将名称带有xx的文件列出来");

        assertThat(response.intentId()).isEqualTo("file_search");
        assertThat(response.actionDraft().type()).isEqualTo("search");
        assertThat(response.entities()).containsEntry("target_name", "xx");
        assertThat(response.missingSlots()).isEmpty();
    }

    @Test
    void localFallbackExtractsNameContainsObjectFromMiddlePredicatePhrase() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("帮我找一下名字中有codex的文件或文件夹");

        assertThat(response.intentId()).isEqualTo("file_search");
        assertThat(response.actionDraft().type()).isEqualTo("search");
        assertThat(response.entities()).containsEntry("target_name", "codex");
        assertThat(response.normalizedQuery()).isEqualTo("帮我找一下名字中有codex的文件或文件夹");
    }

    @Test
    void localFallbackRecognizesNameContainsDeleteAsCollectionDelete() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("我要删除文件名带有xx的文件");

        assertThat(response.intentId()).isEqualTo("collection_delete_by_name");
        assertThat(response.actionDraft().type()).isEqualTo("collection.trash_by_name_contains");
        assertThat(response.entities()).containsEntry("target_name", "xx");
        assertThat(response.safety().risk()).isEqualTo("high");
    }

    @Test
    void localFallbackRecognizesCategoryBatchMove() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("把所有图片移动到图片目录");

        assertThat(response.intentId()).isEqualTo("collection_move_by_category");
        assertThat(response.actionDraft().type()).isEqualTo("collection.move_by_category");
        assertThat(response.entities())
                .containsEntry("file_type", "图片")
                .containsEntry("target_folder", "图片目录");
        assertThat(response.missingSlots()).isEmpty();
    }

    @Test
    void localFallbackSeparatesMoveSourceFromDestination() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("把项目计划文件夹移动到资料目录");

        assertThat(response.intentId()).isEqualTo("node_move");
        assertThat(response.entities())
                .containsEntry("target_name", "项目计划")
                .containsEntry("target_folder", "资料目录");
    }

    @Test
    void localFallbackPreservesDirectorySuffixAndDoesNotInventNumericExtension() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse directory = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("把测试目录移动到文件记录");
        IntentRecognitionResponse numberedDirectory = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("把测试目录2移动到文件记录");

        assertThat(directory.entities())
                .containsEntry("target_name", "测试目录")
                .containsEntry("target_folder", "文件记录")
                .containsEntry("result_type", "FOLDER");
        assertThat(numberedDirectory.entities())
                .containsEntry("target_name", "测试目录2")
                .containsEntry("target_folder", "文件记录")
                .doesNotContainKey("extension");
    }

    @Test
    void localFallbackPreservesFileExtensionInExactMoveTarget() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("把合同.pdf移动到资料目录");

        assertThat(response.intentId()).isEqualTo("node_move");
        assertThat(response.entities())
                .containsEntry("target_name", "合同.pdf")
                .containsEntry("target_folder", "资料目录")
                .containsEntry("extension", "PDF");
    }

    @Test
    void localFallbackRecognizesExecutableBatchRenamePrefixPlan() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("将名称带有xx的文件重命名，统一在头部加上yy");

        assertThat(response.intentId()).isEqualTo("collection_rename_add_prefix");
        assertThat(response.actionDraft().type()).isEqualTo("collection.rename_add_prefix");
        assertThat(response.entities())
                .containsEntry("target_name", "xx")
                .containsEntry("rename_prefix", "yy");
        assertThat(response.assistantText())
                .contains("新旧名称对照")
                .contains("确认");
    }

    @Test
    void localFallbackRecognizesVideoListAsSearch() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("将视频文件全部找出后列出来");

        assertThat(response.intentId()).isEqualTo("file_search");
        assertThat(response.actionDraft().type()).isEqualTo("search");
        assertThat(response.entities()).containsEntry("file_type", "视频");
        assertThat(response.safety().risk()).isEqualTo("none");
    }

    @Test
    void localFallbackRecognizesAssistantIdentity() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("你是谁");

        assertThat(response.intentId()).isEqualTo("assistant_identity");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.actionDraft().needsBackendBinding()).isFalse();
        assertThat(response.assistantText()).contains("安安").contains("文件管家");
        assertThat(response.missingSlots()).isEmpty();
    }

    @Test
    void localFallbackRecognizesAssistantCapabilitiesQuestion() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("你的能力是什么呢？可以为我做什么呢？");

        assertThat(response.intentId()).isEqualTo("assistant_identity");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.assistantText())
                .contains("Alicia")
                .contains("安安")
                .contains("找文件");
        assertThat(response.missingSlots()).isEmpty();
    }

    @Test
    void localFallbackRecognizesAssistantCapabilityExamples() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("详细举例，看看你的能力项");

        assertThat(response.intentId()).isEqualTo("assistant_capability_examples");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.assistantText())
                .contains("比如")
                .contains("确认");
        assertThat(response.missingSlots()).isEmpty();
    }

    @Test
    void localFallbackRecognizesAssistantCapabilityDetails() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("详细说说你的能力");

        assertThat(response.intentId()).isEqualTo("assistant_capability_examples");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.assistantText()).contains("比如");
    }

    @Test
    void localFallbackDoesNotTreatExampleNamedFileSearchAsCapabilityExamples() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("帮我找例子文件");

        assertThat(response.intentId()).isEqualTo("file_search");
        assertThat(response.actionDraft().type()).isEqualTo("search");
        assertThat(response.entities().get("target_name")).asString().contains("例子");
    }

    @Test
    void localFallbackRecognizesCasualAcknowledgement() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("好吧，了解了");

        assertThat(response.intentId()).isEqualTo("assistant_acknowledgement");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.assistantText()).contains("我在");
    }

    @Test
    void localFallbackRecognizesAssistantHelpScopeQuestion() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("你可以帮助做什么呢？");

        assertThat(response.intentId()).isEqualTo("assistant_identity");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.assistantText())
                .contains("安安")
                .contains("文件管家");
        assertThat(response.missingSlots()).isEmpty();
    }

    @Test
    void localFallbackRecognizesAssistantUserIdentityQuestion() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("那么我是谁呢");

        assertThat(response.intentId()).isEqualTo("assistant_user_identity");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.actionDraft().needsBackendBinding()).isFalse();
        assertThat(response.assistantText()).contains("不能凭空知道").contains("当前登录会话");
        assertThat(response.missingSlots()).isEmpty();
    }

    @Test
    void localFallbackRecognizesAssistantSocialCompliment() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("你的形象真漂亮");

        assertThat(response.intentId()).isEqualTo("assistant_social");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.assistantText()).contains("谢谢").contains("文件");
        assertThat(response.safety().risk()).isEqualTo("none");
    }

    @Test
    void localFallbackRecognizesAssistantChat() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("可以和我聊聊天吗");

        assertThat(response.intentId()).isEqualTo("assistant_chat");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.assistantText()).contains("可以").contains("文件");
        assertThat(response.safety().requiresConfirmation()).isFalse();
    }

    @Test
    void localFallbackRecognizesExternalMovieResourceBoundary() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("你知道最近有一部新电影吗，名字叫xx，可以帮我找一下资源吗");

        assertThat(response.intentId()).isEqualTo("assistant_external_resource");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.actionDraft().needsBackendBinding()).isFalse();
        assertThat(response.assistantText()).contains("不能").contains("云盘");
        assertThat(response.safety().requiresConfirmation()).isFalse();
    }

    @Test
    void localFallbackRecognizesWritingHelp() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("我有一个xx文档要写，你可以辅导我吗？");

        assertThat(response.intentId()).isEqualTo("assistant_writing_help");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.assistantText()).contains("提纲").contains("段落");
        assertThat(response.safety().risk()).isEqualTo("none");
    }

    @Test
    void localFallbackRecognizesUnsupportedErrand() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("我想吃东西，帮我点个xx");

        assertThat(response.intentId()).isEqualTo("assistant_errand_unsupported");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.assistantText()).contains("不能").contains("点餐");
        assertThat(response.missingSlots()).isEmpty();
    }

    @Test
    void localFallbackRecognizesBoredCompanionChat() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("我好无聊");

        assertThat(response.intentId()).isEqualTo("assistant_bored");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.assistantText()).contains("陪你").contains("整理");
        assertThat(response.safety().requiresConfirmation()).isFalse();
    }

    @Test
    void localFallbackRecognizesProductInfo() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("这个网盘的优势是什么");

        assertThat(response.intentId()).isEqualTo("assistant_product_info");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.assistantText()).contains("Alicia").contains("优势");
        assertThat(response.actionDraft().needsBackendBinding()).isFalse();
    }

    @Test
    void localFallbackRecognizesProductReason() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("给我一个使用这个网盘的理由");

        assertThat(response.intentId()).isEqualTo("assistant_product_reason");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.assistantText()).contains("理由").contains("安安");
        assertThat(response.safety().risk()).isEqualTo("none");
    }

    @Test
    void localFallbackRecognizesProductFeedback() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("功能有点少呀");

        assertThat(response.intentId()).isEqualTo("assistant_product_feedback");
        assertThat(response.nextAction()).isEqualTo("respond_only");
        assertThat(response.assistantText()).contains("功能").contains("逐步");
        assertThat(response.missingSlots()).isEmpty();
    }

    @Test
    void configuredCapabilityBoundaryPreventsModelMisclassificationAndExecutionDraft() {
        AtomicInteger modelCalls = new AtomicInteger();
        IntentModelClient modelClient = message -> {
            modelCalls.incrementAndGet();
            return Optional.of(new IntentModelClient.ModelIntentResult(
                    "deepseek",
                    "deepseek-v4-flash",
                    "deepseek_file_intent_recognition",
                    "intent_recognition_v1",
                    Map.of("intent_id", "folder_create_then_upload")
            ));
        };

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("如果图片目录不存在就先新建，再把图片全部放进去");

        assertThat(modelCalls).hasValue(0);
        assertThat(response.intentId()).isEqualTo("fallback");
        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.actionDraft().needsBackendBinding()).isFalse();
        assertThat(response.safety().allowedToExecute()).isFalse();
        assertThat(response.assistantText()).contains("不能").contains("先让我新建目标文件夹");
        assertThat(response.fallbackReason()).startsWith("capability_boundary:");
        assertThat(response.actionPlan().version()).isEqualTo("action_plan_v2");
    }

    @Test
    void localFallbackRecognizesNameContainsSynonymForBatchFolderMove() {
        IntentModelClient unavailableClient = message -> Optional.empty();

        IntentRecognitionResponse response = new IntentRecognitionService(unavailableClient, intentRouter, configLoader)
                .recognize("把名称中有调测的文件夹移动到测试目录");

        assertThat(response.intentId()).isEqualTo("collection_move_by_name");
        assertThat(response.semanticFrame().operation()).isEqualTo("MOVE");
        assertThat(response.actionDraft().type()).isEqualTo("collection.move_by_name_contains");
        assertThat(response.entities())
                .containsEntry("target_name", "调测")
                .containsEntry("target_folder", "测试目录");
    }

    @Test
    void authoritativeCollectionRuleOverridesConfidentAtomicModelGuess() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_semantic_frame_v2",
                "semantic_frame_v2",
                Map.ofEntries(
                        Map.entry("intent_id", "node_move"),
                        Map.entry("confidence", 0.99),
                        Map.entry("entities", Map.of("target_name", "调测", "target_folder", "测试目录")),
                        Map.entry("missing_slots", List.of()),
                        Map.entry("assistant_text", "我会先批量匹配名称中带有调测的文件夹，再整理移动计划。")
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("名称中有调测的文件夹都搬到测试目录");

        assertThat(response.intentId()).isEqualTo("collection_move_by_name");
        assertThat(response.actionDraft().type()).isEqualTo("collection.move_by_name_contains");
        assertThat(response.entities())
                .containsEntry("target_name", "调测")
                .containsEntry("target_folder", "测试目录");
        assertThat(response.assistantText()).contains("批量").contains("调测");
    }

    @Test
    void authoritativeRouteRejectsModelReplyWithConflictingEntitySurface() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_semantic_frame_v2",
                "semantic_frame_v2",
                Map.ofEntries(
                        Map.entry("intent_id", "node_move"),
                        Map.entry("confidence", 0.99),
                        Map.entry("entities", Map.of("target_name", "调测", "target_folder", "测试")),
                        Map.entry("missing_slots", List.of()),
                        Map.entry("assistant_text", "我会把名称中带调测的文件夹都移到测试。")
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("名称中有调测的文件夹都搬到测试目录");

        assertThat(response.intentId()).isEqualTo("collection_move_by_name");
        assertThat(response.entities()).containsEntry("target_folder", "测试目录");
        assertThat(response.assistantText())
                .contains("目标文件夹")
                .doesNotContain("移到测试");
    }

    @Test
    void backendBindingStageRejectsPrematureExecutionConfirmationReply() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_semantic_frame_v2",
                "semantic_frame_v2",
                Map.ofEntries(
                        Map.entry("intent_id", "collection_move_by_name"),
                        Map.entry("confidence", 0.99),
                        Map.entry("entities", Map.of("target_name", "调测", "target_folder", "测试目录")),
                        Map.entry("missing_slots", List.of()),
                        Map.entry("assistant_text", "我会把名称中带调测的文件夹都移到测试目录，请确认执行。")
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("名称中有调测的文件夹都搬到测试目录");

        assertThat(response.nextAction()).isEqualTo("wait_for_backend_binding");
        assertThat(response.assistantText())
                .contains("先")
                .contains("匹配")
                .doesNotContain("确认执行");
    }

    @Test
    void underspecifiedImperativeOverridesConfidentModelGuessWithClarification() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_semantic_frame_v2",
                "semantic_frame_v2",
                Map.ofEntries(
                        Map.entry("intent_id", "assistant_acknowledgement"),
                        Map.entry("confidence", 0.99),
                        Map.entry("entities", Map.of()),
                        Map.entry("missing_slots", List.of()),
                        Map.entry("assistant_text", "好的，交给我吧。")
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("这个事情你看着办");

        assertThat(response.intentId()).isEqualTo("fallback");
        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.assistantText()).contains("明确一点");
        assertThat(response.actionDraft().needsBackendBinding()).isFalse();
    }

    @Test
    void completeAuthoritativeRouteIgnoresModelInventedAmbiguity() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_semantic_frame_v2",
                "semantic_frame_v2",
                Map.ofEntries(
                        Map.entry("intent_id", "collection_move_by_name"),
                        Map.entry("confidence", 0.99),
                        Map.entry("entities", Map.of("target_name", "草稿", "target_folder", "临时区")),
                        Map.entry("missing_slots", List.of()),
                        Map.entry("assistant_text", "我会先匹配名称中含草稿的文件，再整理移动到临时区的计划。"),
                        Map.entry("semantic_frame", Map.ofEntries(
                                Map.entry("schema_version", "semantic_frame_v2"),
                                Map.entry("relation", "NEW_TASK"),
                                Map.entry("operation", "MOVE"),
                                Map.entry("query", Map.of("mode", "NAME_CONTAINS", "result_type", "FILE", "name_surface", "草稿")),
                                Map.entry("scope", Map.of("type", "ALL", "folder_surface", "")),
                                Map.entry("reference", Map.of("type", "NONE")),
                                Map.entry("confidence", 0.69),
                                Map.entry("ambiguities", List.of("目标文件夹位置不明确")),
                                Map.entry("clarification", Map.of(
                                        "reason", "目标文件夹位置不明确",
                                        "question", "临时区具体指哪个文件夹？",
                                        "suggestions", List.of()
                                ))
                        ))
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("名称里含草稿的文件都搬到临时区");

        assertThat(response.intentId()).isEqualTo("collection_move_by_name");
        assertThat(response.entities()).containsEntry("target_folder", "临时区");
        assertThat(response.missingSlots()).isEmpty();
        assertThat(response.nextAction()).isEqualTo("wait_for_backend_binding");
        assertThat(response.semanticFrame().needsClarification()).isFalse();
        assertThat(response.assistantText()).doesNotContain("无");
    }

    @Test
    void rejectsIrreversibleClaimForRecoverableTrashOperation() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_semantic_frame_v2",
                "semantic_frame_v2",
                Map.ofEntries(
                        Map.entry("intent_id", "collection_delete_by_name"),
                        Map.entry("confidence", 0.98),
                        Map.entry("entities", Map.of("target_name", "日志")),
                        Map.entry("missing_slots", List.of()),
                        Map.entry("assistant_text", "名称里有日志的文件会被永久删除且不可恢复。")
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("名称里有日志的文件全部删掉");

        assertThat(response.intentId()).isEqualTo("collection_delete_by_name");
        assertThat(response.assistantText())
                .contains("回收站")
                .doesNotContain("永久删除", "不可恢复", "无法恢复");
    }

    @Test
    void rejectsUnsupportedBusinessClassificationCapabilityOverclaim() {
        IntentModelClient modelClient = message -> Optional.of(new IntentModelClient.ModelIntentResult(
                "deepseek",
                "deepseek-v4-flash",
                "deepseek_semantic_frame_v2",
                "semantic_frame_v2",
                Map.ofEntries(
                        Map.entry("intent_id", "assistant_capability_examples"),
                        Map.entry("confidence", 0.97),
                        Map.entry("entities", Map.of()),
                        Map.entry("missing_slots", List.of()),
                        Map.entry("assistant_text", "可以，我能自动识别合同和发票的业务类型并归档。")
                )
        ));

        IntentRecognitionResponse response = new IntentRecognitionService(modelClient, intentRouter, configLoader)
                .recognize("能按合同、发票这种业务类型归档吗");

        assertThat(response.intentId()).isEqualTo("assistant_capability_examples");
        assertThat(response.assistantText())
                .contains("不能")
                .contains("名称关键字")
                .doesNotContain("能自动识别");
        assertThat(response.actionDraft().needsBackendBinding()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object source) {
        return (Map<String, Object>) source;
    }

    private static Map<String, Object> map(Map<String, Object> source, String key) {
        return map(source.get(key));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> source, String key) {
        return (List<Object>) source.get(key);
    }
}
