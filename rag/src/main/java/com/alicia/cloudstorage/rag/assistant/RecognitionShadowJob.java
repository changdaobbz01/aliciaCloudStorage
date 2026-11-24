package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.alicia.cloudstorage.rag.assistant.RecognitionTrace.Outcome;
import static com.alicia.cloudstorage.rag.assistant.RecognitionTrace.Stage;

/** Evaluates first-turn recognition in a separate process on packaged synthetic inputs. */
@Component
@Profile("recognition-shadow")
public class RecognitionShadowJob {

    private static final String DATASET = "rag/recognition-shadow/recognition-search-challenge.json";
    private static final String DATASET_VERSION = "rag_recognition_search_challenge_v1";
    private static final int CASE_LIMIT = 12;
    private static final Set<Stage> OUTSIDE_RECOGNITION = Set.of(
            Stage.SESSION, Stage.SESSION_STORAGE, Stage.CANDIDATE_BINDING, Stage.ACTION_PLAN,
            Stage.REPLY, Stage.REGISTRATION
    );
    private static final Set<String> LABELS = Set.of(
            "intentId", "semanticOperation", "queryMode", "scopeType", "resultType", "nextAction",
            "entityEquals", "noExecutablePlan"
    );
    private static final AssistantConversationState FIRST_TURN = new AssistantConversationState(
            "", 0, "", Map.of(), List.of(), null, null, null, null, null, "", null
    );
    private static final AssistantClientContext CLIENT = new AssistantClientContext(
            null, "/", Map.of(), "action_bridge_v2", List.of()
    );

    private final AssistantSemanticOrchestrator recognizer;
    private final DeepSeekIntentClient model;
    private final RecognitionTraceRecorder traces;
    private final RagConfigLoader configs;
    private final ObjectMapper objectMapper;

    RecognitionShadowJob(AssistantSemanticOrchestrator recognizer, DeepSeekIntentClient model,
                         RecognitionTraceRecorder traces, RagConfigLoader configs, ObjectMapper objectMapper) {
        this.recognizer = recognizer;
        this.model = model;
        this.traces = traces;
        this.configs = configs;
        this.objectMapper = objectMapper;
    }

    public void run(boolean online) {
        if (model.isConfigured() != online) {
            throw new IllegalStateException("The recognition shadow model configuration does not match the requested mode.");
        }
        JsonNode dataset = configs.loadJson(DATASET);
        if (!DATASET_VERSION.equals(dataset.path("version").asText())
                || !"search-challenge".equals(dataset.path("split").asText())
                || dataset.path("gates").path("maximumUnsafeExecutableCount").asInt(-1) != 0) {
            throw new IllegalStateException("The embedded recognition shadow dataset has an invalid manifest.");
        }
        List<Probe> probes = validatedProbes(dataset.path("cases"));
        List<ProbeResult> results = new ArrayList<>(probes.size());
        for (Probe probe : probes) {
            ProbeResult result = evaluate(probe);
            results.add(result);
            if (!result.recognitionOnly() || result.modelRequests() > (online ? 1 : 0)
                    || (result.modelRequests() > 0 && result.modelOutcome() != Outcome.SUCCESS)) {
                break;
            }
        }
        long matched = results.stream().filter(ProbeResult::contractMatched).count();
        int modelRequests = results.stream().mapToInt(ProbeResult::modelRequests).sum();
        boolean passed = results.size() == probes.size() && matched == probes.size()
                && results.stream().allMatch(ProbeResult::recognitionOnly)
                && (online ? modelRequests > 0 : modelRequests == 0)
                && results.stream().allMatch(result -> result.modelRequests() <= 1
                        && (result.modelRequests() == 0 || result.modelOutcome() == Outcome.SUCCESS));
        print(new Report(DATASET_VERSION, online, passed, probes.size(), matched, modelRequests, List.copyOf(results)));
        if (!passed) {
            throw new IllegalStateException("Recognition shadow verification failed.");
        }
    }

    private ProbeResult evaluate(Probe probe) {
        RecognitionTrace trace = traces.start(RecognitionTrace.Route.RECOGNITION_SHADOW);
        if (trace == null) {
            throw new IllegalStateException("Recognition shadow requires a trace for the safety and usage checks.");
        }
        IntentRecognitionResponse response = RecognitionTrace.call(trace, () -> recognizer.decide(
                probe.message(), FIRST_TURN, CLIENT, AssistantClientEvent.none(), false
        ).response());
        trace.finish(Outcome.SUCCESS);
        RecognitionTrace.Snapshot snapshot = traces.find(trace.id()).orElseThrow();
        List<RecognitionTrace.Step> calls = snapshot.steps().stream()
                .filter(step -> step.stage() == Stage.MODEL_CALL && step.outcome() != Outcome.DISABLED)
                .toList();
        boolean recognitionOnly = hasNoMutationDraft(response)
                && snapshot.steps().stream().noneMatch(step -> OUTSIDE_RECOGNITION.contains(step.stage()));
        Outcome modelOutcome = calls.isEmpty() ? Outcome.SKIPPED : calls.getLast().outcome();
        return new ProbeResult(probe.id(), recognitionOnly && matches(probe.expected(), response),
                recognitionOnly, calls.size(), modelOutcome, snapshot.elapsedMicros());
    }

    private static List<Probe> validatedProbes(JsonNode cases) {
        if (!cases.isArray() || cases.size() != CASE_LIMIT) {
            throw new IllegalStateException("The embedded recognition shadow dataset must contain exactly 12 cases.");
        }
        Set<String> ids = new HashSet<>();
        List<Probe> probes = new ArrayList<>(cases.size());
        for (JsonNode item : cases) {
            String id = item.path("id").asText();
            JsonNode turns = item.path("turns");
            JsonNode turn = turns.path(0);
            JsonNode expected = turn.path("expected");
            String message = turn.path("message").asText();
            if (!id.matches("search-challenge\\.[a-z0-9_]{1,64}") || !ids.add(id)
                    || !turns.isArray() || turns.size() != 1 || message.isBlank() || message.length() > 4096
                    || !expected.path("noExecutablePlan").asBoolean()
                    || !expected.path("intentId").isArray() || expected.path("intentId").isEmpty()
                    || expected.properties().stream().anyMatch(entry -> !LABELS.contains(entry.getKey()))) {
                throw new IllegalStateException("The embedded recognition shadow dataset has an invalid case.");
            }
            probes.add(new Probe(id, message, expected));
        }
        return List.copyOf(probes);
    }

    private static boolean matches(JsonNode expected, IntentRecognitionResponse response) {
        SemanticFrame frame = response.semanticFrame();
        return accepts(expected, "intentId", response.intentId())
                && accepts(expected, "semanticOperation", frame.operation())
                && accepts(expected, "queryMode", frame.query().mode())
                && accepts(expected, "scopeType", frame.scope().type())
                && accepts(expected, "resultType", frame.query().resultType())
                && accepts(expected, "nextAction", response.nextAction())
                && expected.path("entityEquals").properties().stream().allMatch(
                        entry -> entry.getValue().asText().equals(response.entities().get(entry.getKey())));
    }

    private static boolean accepts(JsonNode expected, String field, String actual) {
        JsonNode allowed = expected.get(field);
        if (allowed == null) {
            return true;
        }
        for (JsonNode value : allowed) {
            if (value.asText().equals(actual)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNoMutationDraft(IntentRecognitionResponse response) {
        ActionDraft draft = response.actionDraft();
        return (draft == null || "none".equals(draft.type()) || "search".equals(draft.type()))
                && (response.actionPlan() == null || response.actionPlan().steps().isEmpty())
                && (response.backendActionDraft() == null || !response.backendActionDraft().executableByBackend())
                && response.executionReference() == null;
    }

    private void print(Report report) {
        try {
            System.out.println("RAG_RECOGNITION_SHADOW_REPORT " + objectMapper.writeValueAsString(report));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize the recognition shadow report.", exception);
        }
    }

    private record Probe(String id, String message, JsonNode expected) { }

    private record ProbeResult(
            String caseId, boolean contractMatched, boolean recognitionOnly,
            int modelRequests, Outcome modelOutcome, long recognitionMicros
    ) { }

    private record Report(
            String datasetVersion, boolean online, boolean passed, int expectedCases,
            long matchedCases, int modelRequests, List<ProbeResult> probes
    ) { }
}
