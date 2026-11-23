package com.alicia.cloudstorage.rag.assistant;

import com.alicia.cloudstorage.rag.execution.ShadowExecutionRegistrar;
import com.alicia.cloudstorage.rag.security.RagAccessAuthorizer;
import com.alicia.cloudstorage.rag.security.RagAccessPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping(produces = "application/json;charset=UTF-8")
public class AssistantController {

    private static final long DEFAULT_STREAM_HEARTBEAT_MILLIS = 3_000L;

    private final AssistantConversationService assistantConversationService;
    private final AssistantPlanStreamService streamService;
    private final RagConfigLoader configLoader;
    private final RagAccessAuthorizer ragAccessAuthorizer;
    private final ShadowExecutionRegistrar shadowExecutionRegistrar;
    private final RecognitionTraceRecorder traceRecorder;
    private final RecognitionFeedbackService feedbackService;

    public AssistantController(
            AssistantConversationService assistantConversationService,
            AssistantPlanStreamService streamService,
            RagConfigLoader configLoader,
            ObjectMapper objectMapper,
            RagAccessAuthorizer ragAccessAuthorizer,
            ShadowExecutionRegistrar shadowExecutionRegistrar,
            RecognitionTraceRecorder traceRecorder
    ) {
        this(assistantConversationService, streamService, configLoader, objectMapper, ragAccessAuthorizer,
                shadowExecutionRegistrar, traceRecorder, null);
    }

    @Autowired
    public AssistantController(
            AssistantConversationService assistantConversationService,
            AssistantPlanStreamService streamService,
            RagConfigLoader configLoader,
            ObjectMapper objectMapper,
            RagAccessAuthorizer ragAccessAuthorizer,
            ShadowExecutionRegistrar shadowExecutionRegistrar,
            RecognitionTraceRecorder traceRecorder,
            RecognitionFeedbackService feedbackService
    ) {
        this.assistantConversationService = assistantConversationService;
        this.streamService = streamService;
        this.configLoader = configLoader;
        this.ragAccessAuthorizer = ragAccessAuthorizer;
        this.shadowExecutionRegistrar = shadowExecutionRegistrar;
        this.traceRecorder = traceRecorder;
        this.feedbackService = feedbackService;
    }

    AssistantController(
            AssistantConversationService assistantConversationService,
            RagConfigLoader configLoader,
            ObjectMapper objectMapper
    ) {
        this(assistantConversationService, configLoader, objectMapper, DEFAULT_STREAM_HEARTBEAT_MILLIS);
    }

    AssistantController(
            AssistantConversationService assistantConversationService,
            RagConfigLoader configLoader,
            ObjectMapper objectMapper,
            long streamHeartbeatMillis
    ) {
        this(
                assistantConversationService,
                configLoader,
                objectMapper,
                streamHeartbeatMillis,
                RagAccessAuthorizer.allowAll(),
                ShadowExecutionRegistrar.disabled()
        );
    }

    AssistantController(
            AssistantConversationService assistantConversationService,
            RagConfigLoader configLoader,
            ObjectMapper objectMapper,
            long streamHeartbeatMillis,
            RagAccessAuthorizer ragAccessAuthorizer
    ) {
        this(
                assistantConversationService,
                configLoader,
                objectMapper,
                streamHeartbeatMillis,
                ragAccessAuthorizer,
                ShadowExecutionRegistrar.disabled()
        );
    }

    AssistantController(
            AssistantConversationService assistantConversationService,
            RagConfigLoader configLoader,
            ObjectMapper objectMapper,
            long streamHeartbeatMillis,
            RagAccessAuthorizer ragAccessAuthorizer,
            ShadowExecutionRegistrar shadowExecutionRegistrar
    ) {
        this.assistantConversationService = assistantConversationService;
        this.streamService = assistantConversationService == null
                ? null
                : new AssistantPlanStreamService(assistantConversationService, streamHeartbeatMillis);
        this.configLoader = configLoader;
        this.ragAccessAuthorizer = ragAccessAuthorizer;
        this.shadowExecutionRegistrar = shadowExecutionRegistrar;
        this.traceRecorder = RecognitionTraceRecorder.disabled();
        this.feedbackService = null;
    }

    @GetMapping("/api/config/client")
    public Map<String, Object> clientConfig() {
        return configLoader.loadJsonMap("rag/ui/client.json");
    }

    @GetMapping("/api/assistant/contracts/action-bridge")
    public Map<String, Object> actionBridgeContract(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        ragAccessAuthorizer.requireRagAdminAccess(authorizationHeader);
        return configLoader.loadJsonMap("rag/conversation/action_bridge.json");
    }

    @GetMapping("/api/assistant/contracts/mobile")
    public Map<String, Object> mobileContract(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        ragAccessAuthorizer.requireRagAdminAccess(authorizationHeader);
        return configLoader.loadJsonMap("rag/conversation/mobile_contract.json");
    }

    @GetMapping("/api/assistant/contracts/capabilities")
    public Map<String, Object> capabilities(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        ragAccessAuthorizer.requireRagAdminAccess(authorizationHeader);
        return configLoader.loadJsonMap("rag/conversation/capabilities.json");
    }

    @GetMapping("/api/assistant/contracts/acceptance-scenarios")
    public Map<String, Object> acceptanceScenarios(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        ragAccessAuthorizer.requireRagAdminAccess(authorizationHeader);
        return configLoader.loadJsonMap("rag/conversation/acceptance_scenarios.json");
    }

    @GetMapping("/api/assistant/contracts/action-plan")
    public Map<String, Object> actionPlanContract(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        ragAccessAuthorizer.requireRagAdminAccess(authorizationHeader);
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("schema", configLoader.loadJsonMap("rag/conversation/action_plan_schema.json"));
        contract.put("actions", configLoader.loadJsonMap("rag/conversation/action_templates.json"));
        contract.put("composites", configLoader.loadJsonMap("rag/conversation/composite_actions.json"));
        contract.put("collections", configLoader.loadJsonMap("rag/conversation/collection_actions.json"));
        contract.put("policies", configLoader.loadJsonMap("rag/conversation/policies.json"));
        contract.put("semanticArbitration", configLoader.loadJsonMap("rag/conversation/semantic_arbitration.json"));
        contract.put("assistantResponsePolicy", configLoader.loadJsonMap("rag/conversation/assistant_response_policy.json"));
        contract.put("dialogue", configLoader.loadJsonMap("rag/conversation/dialogue_templates.json"));
        contract.put("persona", configLoader.loadJsonMap("rag/conversation/persona.json"));
        contract.put("mobile", configLoader.loadJsonMap("rag/conversation/mobile_contract.json"));
        contract.put("acceptanceScenarios", configLoader.loadJsonMap("rag/conversation/acceptance_scenarios.json"));
        return contract;
    }

    @GetMapping("/api/assistant/auth/access")
    public Map<String, Object> access(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        RagAccessPrincipal principal = ragAccessAuthorizer.requireRagAccess(authorizationHeader);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("appCode", principal.appCode());
        response.put("role", principal.role());
        response.put("userId", principal.userId());
        return Map.copyOf(response);
    }

    @PostMapping("/api/assistant/plan")
    public IntentRecognitionResponse plan(
            @Valid @NotNull @RequestBody AssistantPlanRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletResponse httpResponse
    ) {
        String message = request == null ? "" : request.message();
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        RagAccessPrincipal principal = ragAccessAuthorizer.requireRagAccess(authorizationHeader);
        AssistantPlanRequest normalized = new AssistantPlanRequest(
                message.trim(), request.conversationId(), request.clientContext(), request.clientEvent());
        RecognitionTrace trace = beginTrace(RecognitionTrace.Route.PLAN, httpResponse, principal);
        if (trace == null) {
            return planAndRegister(normalized, authorizationHeader);
        }
        try {
            IntentRecognitionResponse result = RecognitionTrace.call(trace, () -> planAndRegister(normalized, authorizationHeader));
            trace.finish(RecognitionTrace.Outcome.SUCCESS);
            return result;
        } catch (RuntimeException failure) {
            trace.finish(RecognitionTrace.Outcome.FAILED);
            throw failure;
        }
    }

    IntentRecognitionResponse plan(AssistantPlanRequest request, String authorizationHeader) {
        return plan(request, authorizationHeader, null);
    }

    private IntentRecognitionResponse planAndRegister(AssistantPlanRequest request, String authorizationHeader) {
        long started = RecognitionTrace.tick();
        IntentRecognitionResponse response = assistantConversationService.plan(request, authorizationHeader);
        response = PublicAssistantResponseSanitizer.sanitize(
                shadowExecutionRegistrar.registerIfEligible(response, authorizationHeader));
        RecognitionTrace.decision(RecognitionTrace.Stage.FINAL, RecognitionTrace.Outcome.OBSERVED, started, response);
        return response;
    }

    @PostMapping(value = "/api/assistant/plan/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter planStream(
            @Valid @NotNull @RequestBody AssistantPlanRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletResponse httpResponse
    ) {
        String message = request == null ? "" : request.message();
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        RagAccessPrincipal principal = ragAccessAuthorizer.requireRagAccess(authorizationHeader);

        String conversationId = request.conversationId();
        AssistantPlanRequest sanitizedRequest = new AssistantPlanRequest(
                message.trim(),
                conversationId,
                request.clientContext(),
                request.clientEvent()
        );

        return streamService.stream(sanitizedRequest, authorizationHeader,
                beginTrace(RecognitionTrace.Route.STREAM, httpResponse, principal));
    }

    @PostMapping("/api/intent/recognize")
    public IntentRecognitionResponse recognize(
            @Valid @NotNull @RequestBody AssistantPlanRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletResponse httpResponse
    ) {
        return plan(request, authorizationHeader, httpResponse);
    }

    private RecognitionTrace beginTrace(RecognitionTrace.Route route, HttpServletResponse response, RagAccessPrincipal principal) {
        Long userId = principal == null ? null : principal.userId();
        RecognitionTrace trace = traceRecorder.start(route, userId);
        if (trace != null && response != null) {
            response.setHeader(RecognitionTrace.HEADER, trace.id().toString());
            if (feedbackService != null && userId != null && userId > 0 && feedbackService.enabled()) {
                response.setHeader(RecognitionFeedbackService.HEADER, feedbackService.mode());
            }
        }
        return trace;
    }

}
