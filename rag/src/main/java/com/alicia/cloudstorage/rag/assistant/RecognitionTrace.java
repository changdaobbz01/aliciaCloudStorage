package com.alicia.cloudstorage.rag.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class RecognitionTrace {

    static final String HEADER = "X-Recognition-Trace-Id";
    private static final int MAX_STEPS = 48;
    private static final ThreadLocal<RecognitionTrace> CURRENT = new ThreadLocal<>();
    private static final Set<String> OPERATIONS = Set.of("UNKNOWN", "RESPOND", "SEARCH", "LIST_CHILDREN",
            "OPEN_FILE", "NAVIGATE", "UPLOAD", "DELETE", "MOVE", "RENAME", "SHARE", "CREATE_FOLDER");
    private static final Set<String> RELATIONS = Set.of("NEW_TASK", "FOLLOW_UP", "CORRECTION", "SLOT_FILL",
            "CANDIDATE_SELECTION", "CONFIRMATION", "CANCELLATION");
    private static final Set<String> QUERY_MODES = Set.of("NONE", "NAME_SEARCH", "NAME_EXACT",
            "NAME_CONTAINS", "LIST_CHILDREN", "FILTER", "COLLECTION");
    private static final Set<String> SCOPES = Set.of("ALL", "ROOT", "CURRENT", "PARENT",
            "NAMED_FOLDER", "PREVIOUS_RESULTS");
    private static final Set<String> REFERENCES = Set.of("NONE", "PREVIOUS_CANDIDATE", "PREVIOUS_CANDIDATE_SET",
            "SELECTED_CANDIDATE", "ANOTHER_CANDIDATE", "REMAINING_CANDIDATES", "PREVIOUS_ACTION", "CLIENT_INPUT");
    private static final Set<String> NEXT_ACTIONS = Set.of("ask_clarification", "respond_only", "wait_for_backend_binding",
            "show_search_results", "wait_for_candidate_selection", "wait_for_user_confirmation",
            "handoff_to_backend", "handoff_to_client_upload");
    private static final Set<String> BINDING_STATES = Set.of("not_requested", "waiting_for_clarification", "search_results_ready",
            "no_candidates", "single_candidate", "multiple_candidates", "selected_candidate", "candidate_selection_out_of_range",
            "missing_query", "unsupported_filter", "storage_api_not_configured", "missing_authorization", "storage_api_error",
            "collection_filter_only", "collection_not_executable", "capability_boundary");
    private static final Set<String> PLAN_STATES = Set.of("completed", "understanding", "clarification_required", "binding_required",
            "candidate_selection_required", "review_required", "collection_review_required", "ready_to_execute",
            "client_input_required", "blocked", "not_requested", "not_confirmed", "unsupported_action");

    private final UUID id = UUID.randomUUID();
    private final Route route;
    private final RecognitionTraceRecorder recorder;
    private final UUID ownerTag;
    private final long started = System.nanoTime();
    private final List<Step> steps = new ArrayList<>();
    private List<Example> examples = List.of();
    private boolean finished;

    RecognitionTrace(Route route, RecognitionTraceRecorder recorder, UUID ownerTag) {
        this.route = route;
        this.recorder = recorder;
        this.ownerTag = ownerTag;
    }

    UUID id() {
        return id;
    }

    static <T> T call(RecognitionTrace trace, Supplier<T> work) {
        if (trace == null) {
            return work.get();
        }
        RecognitionTrace previous = CURRENT.get();
        CURRENT.set(trace);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static long tick() {
        return CURRENT.get() == null ? 0L : System.nanoTime();
    }

    static void mark(Stage stage, Outcome outcome, long since) {
        RecognitionTrace trace = CURRENT.get();
        if (trace != null) {
            trace.add(stage, outcome, since, null, null, null);
        }
    }

    static void session(Outcome outcome, long since, AssistantConversationState state) {
        RecognitionTrace trace = CURRENT.get();
        if (trace != null) {
            trace.add(Stage.SESSION, outcome, since, null, new Context(state.turnIndex(),
                    state.pendingSlots().size(), state.focus().candidateCount(), state.pendingActionPlan() != null), null);
        }
    }

    static void retrieval(long since, List<SemanticExampleRetriever.SemanticExample> found) {
        RecognitionTrace trace = CURRENT.get();
        if (trace != null) {
            trace.addRetrieval(since, found);
        }
    }

    static void modelCall(Outcome outcome, long since, String model, String promptVersion, int httpStatus) {
        RecognitionTrace trace = CURRENT.get();
        if (trace != null) {
            trace.add(Stage.MODEL_CALL, outcome, since, null, null, new Model(
                    approved(model, Set.of("deepseek-v4-flash")),
                    approved(promptVersion, Set.of("semantic_frame_v2", "intent_recognition_v1")),
                    httpStatus >= 100 && httpStatus <= 599 ? httpStatus : 0));
        }
    }

    static void modelProposal(long since, Map<String, Object> payload) {
        RecognitionTrace trace = CURRENT.get();
        if (trace == null) {
            return;
        }
        Map<?, ?> frame = object(payload.get("semantic_frame"));
        Map<?, ?> query = object(frame.get("query"));
        Map<?, ?> scope = object(frame.get("scope"));
        Map<?, ?> reference = object(frame.get("reference"));
        Decision shape = new Decision(trace.recorder.intent(payload.get("intent_id")), approved(frame.get("operation"), OPERATIONS),
                approved(frame.get("relation"), RELATIONS), approved(query.get("mode"), QUERY_MODES),
                approved(scope.get("type"), SCOPES), approved(reference.get("type"), REFERENCES),
                approved(payload.get("next_action"), NEXT_ACTIONS), object(payload.get("entities")).size(),
                count(payload.get("missing_slots")), count(frame.get("ambiguities")) > 0
                || !object(frame.get("clarification")).isEmpty(), "", 0, "", 0, false);
        trace.add(Stage.MODEL_PROPOSAL, Outcome.OBSERVED, since, shape, null, null);
    }

    static void localRoute(long since, IntentRouter.IntentRouteResult route) {
        RecognitionTrace trace = CURRENT.get();
        if (trace != null) {
            Decision shape = new Decision(trace.recorder.intent(route.intent()), "", "", "", "", "", "",
                    route.entities().size(), route.missingSlots().size(), false, "", 0, "", 0, false);
            trace.add(Stage.LOCAL_ROUTE, Outcome.OBSERVED, since, shape, null, null);
        }
    }

    static void decision(Stage stage, Outcome outcome, long since, IntentRecognitionResponse response) {
        RecognitionTrace trace = CURRENT.get();
        if (trace != null) {
            trace.add(stage, outcome, since, trace.project(response), null, null);
        }
    }

    public static void registration(Outcome outcome, long since) {
        mark(Stage.REGISTRATION, outcome, since);
    }

    synchronized void finish(Outcome outcome) {
        if (finished) {
            return;
        }
        finished = true;
        recorder.save(new Snapshot(id, route, outcome, elapsed(started), List.copyOf(steps), examples), ownerTag);
    }

    private synchronized void addRetrieval(long since, List<SemanticExampleRetriever.SemanticExample> found) {
        if (finished) {
            return;
        }
        examples = found.stream()
                .filter(example -> recorder.isCorpusId(example.id()))
                .filter(example -> Double.isFinite(example.score()))
                .limit(5)
                .map(example -> new Example(example.id(), (int) Math.round(example.score() * 1000)))
                .toList();
        add(Stage.RETRIEVAL, found.isEmpty() ? Outcome.NO_MATCH : Outcome.OBSERVED, since, null, null, null);
    }

    private synchronized void add(Stage stage, Outcome outcome, long since, Decision decision, Context context, Model model) {
        if (!finished && steps.size() < MAX_STEPS) {
            steps.add(new Step(stage, outcome, elapsed(since), decision, context, model));
        }
    }

    private Decision project(IntentRecognitionResponse response) {
        if (response == null) {
            return null;
        }
        SemanticFrame frame = response.semanticFrame() == null ? SemanticFrame.empty() : response.semanticFrame();
        CandidateBindingResult binding = response.candidateBinding();
        ActionPlan plan = response.actionPlan();
        return new Decision(recorder.intent(response.intentId()), approved(frame.operation(), OPERATIONS),
                approved(frame.relation(), RELATIONS), approved(frame.query().mode(), QUERY_MODES),
                approved(frame.scope().type(), SCOPES), approved(frame.reference().type(), REFERENCES),
                approved(response.nextAction(), NEXT_ACTIONS), object(response.entities()).size(), count(response.missingSlots()),
                frame.needsClarification() || "ask_clarification".equals(response.nextAction()),
                binding == null ? "" : approved(binding.status(), BINDING_STATES),
                binding == null ? 0 : count(binding.candidates()),
                plan == null ? "" : approved(plan.status(), PLAN_STATES), plan == null ? 0 : count(plan.steps()),
                response.executionReference() != null);
    }

    private static long elapsed(long since) {
        return since == 0L ? 0L : TimeUnit.NANOSECONDS.toMicros(Math.max(0L, System.nanoTime() - since));
    }

    private static String approved(Object value, Set<String> allowed) {
        return value instanceof String label && allowed.contains(label) ? label : "";
    }

    private static Map<?, ?> object(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static int count(List<?> value) {
        return value == null ? 0 : value.size();
    }

    private static int count(Object value) {
        return value instanceof List<?> list ? count(list) : 0;
    }

    enum Route { PLAN, STREAM }
    enum Stage {
        SESSION, SESSION_STORAGE, ROUTING, FOLLOW_UP_GUARD, OPERATION_FOLLOW_UP, NON_EXECUTABLE_GUARD, CAPABILITY_GUARD,
        RETRIEVAL, EMBEDDING, MODEL_CALL, MODEL_RESULT, MODEL_PROPOSAL, LOCAL_ROUTE, ARBITRATION, SEMANTIC_FRAME,
        COLLECTION_ROUTING, NAVIGATION, CONSTRAINT_GUARD, CLIENT_CAPABILITY, CANDIDATE_SELECTION,
        CONTEXT_RESOLUTION, PENDING_INTENT, CANDIDATE_BINDING, ACTION_PLAN, REPLY, REGISTRATION, FINAL
    }
    public enum Outcome {
        OBSERVED, SUCCESS, FAILED, TIMED_OUT, CANCELLED, SKIPPED, NO_MATCH, UNAVAILABLE,
        CREATED, RESTORED, RESET, TERMINAL_EVENT, LOCAL, MODEL, LOCAL_STRUCTURE, BLOCKED,
        CLARIFIED, REWRITTEN, SLOT_FILLED, CONTINUED, SELECTED, DISABLED, NOT_ELIGIBLE,
        INVALID_JSON, UNUSABLE_RESPONSE, HTTP_ERROR, TRANSPORT_ERROR, ACTION_NOT_ENABLED,
        ACCESS_DENIED, REGISTRATION_REJECTED, REGISTRATION_UNCERTAIN, RECOVERED
    }

    record Snapshot(UUID recognitionTraceId, Route route, Outcome outcome, long elapsedMicros,
                    List<Step> steps, List<Example> examples) {}
    record Step(Stage stage, Outcome outcome, long elapsedMicros, Decision decision, Context context, Model model) {}
    record Decision(String intent, String operation, String relation, String queryMode, String scope,
                    String reference, String nextAction, int entityCount, int missingSlots, boolean needsClarification,
                    String bindingStatus, int candidateCount, String planStatus, int planSteps, boolean hasExecutionReference) {}
    record Context(int previousTurns, int pendingSlots, int candidateCount, boolean hasPendingPlan) {}
    record Model(String model, String promptVersion, int httpStatus) {}
    record Example(String corpusId, int scorePermille) {}
}
