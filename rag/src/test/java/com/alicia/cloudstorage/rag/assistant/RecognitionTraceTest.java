package com.alicia.cloudstorage.rag.assistant;

import com.alicia.cloudstorage.rag.execution.BatchSnapshotFingerprint;
import com.alicia.cloudstorage.rag.execution.RagExecutionRegistrationProperties;
import com.alicia.cloudstorage.rag.execution.RegistrationPlanHasher;
import com.alicia.cloudstorage.rag.execution.ShadowExecutionRegistrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecognitionTraceTest {
    private final ObjectMapper jackson = new ObjectMapper().findAndRegisterModules();
    private final RagConfigLoader config = new RagConfigLoader(jackson);
    private final RecognitionTraceRecorder recorder = new RecognitionTraceRecorder(true, config);

    @Test
    void serializesOnlyAllowlistedModelAndCorpusMetadata() throws Exception {
        String privateValue = "PRIVATE_TOKEN_file-name_user-8722";
        var trace = recorder.start(RecognitionTrace.Route.PLAN);
        var approvedExample = new CorpusSemanticExampleRetriever(config).retrieve("在当前目录新建一个文件夹", 1).getFirst();
        var untrustedExample = new SemanticExampleRetriever.SemanticExample(
                privateValue, "/cloud/private-folder", "file_search", "SEARCH", Map.of("nodeId", 8722), 1);
        RecognitionTrace.call(trace, () -> {
            RecognitionTrace.retrieval(RecognitionTrace.tick(), List.of(untrustedExample, approvedExample));
            RecognitionTrace.modelCall(RecognitionTrace.Outcome.SUCCESS, RecognitionTrace.tick(),
                    "deepseek-v4-flash", privateValue, 200);
            RecognitionTrace.modelProposal(RecognitionTrace.tick(), Map.of(
                    "intent_id", "file_search",
                    "entities", Map.of("nodeId", 8722, "folder", privateValue),
                    "assistant_text", privateValue,
                    "next_action", privateValue,
                    "semantic_frame", Map.of(
                            "operation", "SEARCH", "relation", privateValue,
                            "query", Map.of("mode", "NAME_EXACT", "name_surface", privateValue),
                            "scope", Map.of("type", privateValue),
                            "clarification", Map.of("question", privateValue))));
            return null;
        });
        trace.finish(RecognitionTrace.Outcome.SUCCESS);
        var snapshot = recorder.find(trace.id()).orElseThrow();

        assertThat(snapshot.examples()).extracting(RecognitionTrace.Example::corpusId).containsExactly(approvedExample.id());
        assertThat(snapshot.steps().get(1).model().promptVersion()).isEmpty();
        assertThat(snapshot.steps().get(2).decision()).satisfies(shape -> {
            assertThat(shape.intent()).isEqualTo("file_search");
            assertThat(shape.operation()).isEqualTo("SEARCH");
            assertThat(shape.queryMode()).isEqualTo("NAME_EXACT");
            assertThat(shape.entityCount()).isEqualTo(2);
            assertThat(shape.scope()).isEmpty();
            assertThat(shape.relation()).isEmpty();
        });
        assertThat(jackson.writeValueAsString(snapshot))
                .doesNotContain(privateValue, "/cloud/private-folder", "nodeId", ":8722", "assistant_text",
                        "name_surface", "conversationId", "Authorization");
    }

    @Test
    void identifiesWhitelistRejectionWithoutRetainingDownstreamBody() throws Exception {
        var trace = recorder.start(RecognitionTrace.Route.PLAN);
        var props = new RagExecutionRegistrationProperties(true, "http://localhost", "local-only-test-registration-signing-secret",
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMillis(200), Duration.ofMillis(10), Duration.ofMinutes(5));
        String sensitiveBody = "{\"error\":\"action_not_enabled\",\"details\":\"PRIVATE_BACKEND_DETAIL\"}";
        var registrar = new ShadowExecutionRegistrationService(props, (request, auth) -> {
            throw HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_CONTENT, "Rejected", HttpHeaders.EMPTY,
                    sensitiveBody.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }, new RegistrationPlanHasher(jackson), jackson, Clock.systemUTC(),
                new CollectionActionSnapshotStore(30, 100), new BatchSnapshotFingerprint());
        var plan = new ActionPlan("action_plan_v2", "ap-local-test", "review_required", "atomic", "node.rename",
                "medium", "candidate_then_final_review", "zh-CN", Map.of(), List.of(
                new ActionPlanStep("node_rename", "node.rename", "pending", Map.of("nodeId", 42L, "name", "private.pdf"),
                        List.of(), List.of(), "renamedNode")), List.of(), "Rename", List.of());
        var router = new IntentRouter(config, new EntityExtractor(config));
        var response = new IntentRecognitionService(message -> Optional.empty(), router, config)
                .recognizeLocal("重命名 private.pdf", "fixture").withActionPlan(plan);

        var result = RecognitionTrace.call(trace, () -> registrar.registerIfEligible(response, "Bearer private-token"));
        trace.finish(RecognitionTrace.Outcome.SUCCESS);
        var snapshot = recorder.find(trace.id()).orElseThrow();

        assertThat(result).isSameAs(response);
        assertThat(snapshot.steps()).singleElement().satisfies(step -> {
            assertThat(step.stage()).isEqualTo(RecognitionTrace.Stage.REGISTRATION);
            assertThat(step.outcome()).isEqualTo(RecognitionTrace.Outcome.ACTION_NOT_ENABLED);
        });
        assertThat(jackson.writeValueAsString(snapshot))
                .doesNotContain("PRIVATE_BACKEND_DETAIL", "ap-local-test", "private-token", "private.pdf");
    }

    @Test
    void clearsFailedThreadScopeAndEvictsOldestCompletedTrace() {
        var first = recorder.start(RecognitionTrace.Route.PLAN);
        assertThatThrownBy(() -> RecognitionTrace.call(first, () -> { throw new IllegalStateException("private"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(RecognitionTrace.tick()).isZero();
        first.finish(RecognitionTrace.Outcome.FAILED);
        first.finish(RecognitionTrace.Outcome.SUCCESS);
        assertThat(recorder.find(first.id()).orElseThrow().outcome()).isEqualTo(RecognitionTrace.Outcome.FAILED);
        for (int index = 0; index < 256; index++) {
            recorder.start(RecognitionTrace.Route.PLAN).finish(RecognitionTrace.Outcome.SUCCESS);
        }
        assertThat(recorder.find(first.id())).isEmpty();
    }
}
