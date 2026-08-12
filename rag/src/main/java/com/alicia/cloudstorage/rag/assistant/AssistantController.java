package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping(produces = "application/json;charset=UTF-8")
public class AssistantController {

    private final AssistantConversationService assistantConversationService;
    private final RagConfigLoader configLoader;
    private final ObjectMapper objectMapper;

    public AssistantController(
            AssistantConversationService assistantConversationService,
            RagConfigLoader configLoader,
            ObjectMapper objectMapper
    ) {
        this.assistantConversationService = assistantConversationService;
        this.configLoader = configLoader;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/config/client")
    public Map<String, Object> clientConfig() {
        return configLoader.loadJsonMap("rag/ui/client.json");
    }

    @GetMapping("/api/assistant/contracts/action-bridge")
    public Map<String, Object> actionBridgeContract() {
        return configLoader.loadJsonMap("rag/conversation/action_bridge.json");
    }

    @GetMapping("/api/assistant/contracts/mobile")
    public Map<String, Object> mobileContract() {
        return configLoader.loadJsonMap("rag/conversation/mobile_contract.json");
    }

    @GetMapping("/api/assistant/contracts/acceptance-scenarios")
    public Map<String, Object> acceptanceScenarios() {
        return configLoader.loadJsonMap("rag/conversation/acceptance_scenarios.json");
    }

    @GetMapping("/api/assistant/contracts/action-plan")
    public Map<String, Object> actionPlanContract() {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("schema", configLoader.loadJsonMap("rag/conversation/action_plan_schema.json"));
        contract.put("actions", configLoader.loadJsonMap("rag/conversation/action_templates.json"));
        contract.put("composites", configLoader.loadJsonMap("rag/conversation/composite_actions.json"));
        contract.put("collections", configLoader.loadJsonMap("rag/conversation/collection_actions.json"));
        contract.put("policies", configLoader.loadJsonMap("rag/conversation/policies.json"));
        contract.put("dialogue", configLoader.loadJsonMap("rag/conversation/dialogue_templates.json"));
        contract.put("persona", configLoader.loadJsonMap("rag/conversation/persona.json"));
        contract.put("mobile", configLoader.loadJsonMap("rag/conversation/mobile_contract.json"));
        contract.put("acceptanceScenarios", configLoader.loadJsonMap("rag/conversation/acceptance_scenarios.json"));
        return contract;
    }

    @PostMapping("/api/assistant/plan")
    public IntentRecognitionResponse plan(
            @RequestBody AssistantPlanRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        String message = request == null ? "" : request.message();
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        String conversationId = request == null ? "" : request.conversationId();
        return assistantConversationService.plan(
                new AssistantPlanRequest(message.trim(), conversationId, request.clientContext(), request.clientEvent()),
                authorizationHeader
        );
    }

    @PostMapping(value = "/api/assistant/plan/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody planStream(
            @RequestBody AssistantPlanRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        String message = request == null ? "" : request.message();
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }

        String conversationId = request == null ? "" : request.conversationId();
        AssistantPlanRequest sanitizedRequest = new AssistantPlanRequest(
                message.trim(),
                conversationId,
                request.clientContext(),
                request.clientEvent()
        );

        return outputStream -> {
            writeStreamEvent(outputStream, AssistantStreamEvent.status("安安正在理解你的意思..."));
            IntentRecognitionResponse response = assistantConversationService.plan(
                    sanitizedRequest,
                    authorizationHeader
            );
            sleepBetweenStreamEvents();
            writeStreamEvent(outputStream, AssistantStreamEvent.status(streamStatusText(response)));
            sleepBetweenStreamEvents();
            for (AssistantStreamEvent event : textChunks(response.assistantText())) {
                writeStreamEvent(outputStream, event);
                sleepBetweenStreamEvents();
            }
            writeStreamEvent(outputStream, AssistantStreamEvent.finalResponse(response));
            writeStreamEvent(outputStream, AssistantStreamEvent.done());
        };
    }

    @PostMapping("/api/intent/recognize")
    public IntentRecognitionResponse recognize(
            @RequestBody AssistantPlanRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return plan(request, authorizationHeader);
    }

    private String streamStatusText(IntentRecognitionResponse response) {
        if (response == null || response.intentName() == null || response.intentName().isBlank()) {
            return "安安正在整理结果...";
        }
        if ("show_search_results".equals(response.nextAction())) {
            return "安安已找到候选，正在展示结果...";
        }
        if ("ask_clarification".equals(response.nextAction())) {
            return "安安还需要补充一点信息...";
        }
        if ("wait_for_user_confirmation".equals(response.nextAction())) {
            return "安安正在生成需要确认的计划...";
        }
        if ("wait_for_backend_binding".equals(response.nextAction())) {
            return "安安正在匹配云盘里的候选...";
        }
        if ("respond_only".equals(response.nextAction())) {
            return "安安正在整理回复...";
        }
        return "安安正在整理下一步...";
    }

    private java.util.List<AssistantStreamEvent> textChunks(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) {
            return java.util.List.of();
        }

        java.util.List<AssistantStreamEvent> events = new java.util.ArrayList<>();
        int index = 0;
        while (index < value.length()) {
            int next = Math.min(value.length(), index + 9);
            events.add(AssistantStreamEvent.delta(value.substring(index, next)));
            index = next;
        }
        return events;
    }

    private void writeStreamEvent(OutputStream outputStream, AssistantStreamEvent event) throws IOException {
        String payload = objectMapper.writeValueAsString(event);
        outputStream.write(("event:" + event.type() + "\n").getBytes(StandardCharsets.UTF_8));
        for (String line : payload.split("\\R", -1)) {
            outputStream.write(("data:" + line + "\n").getBytes(StandardCharsets.UTF_8));
        }
        outputStream.write("\n".getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private void sleepBetweenStreamEvents() {
        try {
            Thread.sleep(90L);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("assistant stream interrupted", error);
        }
    }
}
