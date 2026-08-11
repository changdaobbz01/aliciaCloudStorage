package com.alicia.cloudstorage.rag.assistant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping(produces = "application/json;charset=UTF-8")
public class AssistantController {

    private final AssistantConversationService assistantConversationService;
    private final RagConfigLoader configLoader;

    public AssistantController(
            AssistantConversationService assistantConversationService,
            RagConfigLoader configLoader
    ) {
        this.assistantConversationService = assistantConversationService;
        this.configLoader = configLoader;
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
                new AssistantPlanRequest(message.trim(), conversationId),
                authorizationHeader
        );
    }

    @PostMapping("/api/intent/recognize")
    public IntentRecognitionResponse recognize(
            @RequestBody AssistantPlanRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        return plan(request, authorizationHeader);
    }
}
