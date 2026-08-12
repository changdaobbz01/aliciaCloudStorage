package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(contract).containsEntry("version", "action_bridge_v1");
        assertThat(actionKeys).contains("rename", "delete", "share", "upload_target");
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
                "dialogue",
                "persona",
                "mobile",
                "acceptanceScenarios"
        );
        assertThat(map(contract.get("schema"))).containsEntry("version", "action_plan_v1");
        assertThat(map(contract.get("actions"))).containsEntry("version", "action_templates_v1");
        assertThat(map(contract.get("composites"))).containsEntry("version", "composite_actions_v1");
        assertThat(map(contract.get("collections"))).containsEntry("version", "collection_actions_v1");
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
                .contains("semantic parser");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object source) {
        return (Map<String, Object>) source;
    }
}
