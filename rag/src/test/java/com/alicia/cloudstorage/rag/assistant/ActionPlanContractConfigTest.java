package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionPlanContractConfigTest {

    private RagConfigLoader configLoader;

    @BeforeEach
    void setUp() {
        configLoader = new RagConfigLoader(new ObjectMapper());
    }

    @Test
    void actionPlanSchemaDefinesStagedPlanningStatesAndSafeExecutionRules() {
        Map<String, Object> schema = configLoader.loadJsonMap("rag/conversation/action_plan_schema.json");
        List<Object> statuses = list(schema, "statuses");
        Map<String, Object> executionRules = map(schema, "executionRules");

        assertThat(schema).containsEntry("version", "action_plan_v1");
        assertThat(statuses).contains(
                "clarification_required",
                "candidate_selection_required",
                "collection_review_required",
                "conflict_resolution_required",
                "review_required",
                "client_input_required",
                "ready_to_execute"
        );
        assertThat(list(schema, "planKinds")).contains("atomic", "composite", "collection");
        assertThat(executionRules).containsEntry("ragExecutesMutations", false);
        assertThat(executionRules).containsEntry("clientMustUseLocalAllowlist", true);
        assertThat(executionRules).containsEntry("clientMustRevalidateBindingsBeforeExecute", true);
    }

    @Test
    void enabledResponseTemplatesDoNotExposeDebugOrImplementationWording() {
        List<Map<String, String>> templates = configLoader.loadCsv("rag/conversation/response_templates.csv");

        for (Map<String, String> template : templates) {
            if (!Boolean.parseBoolean(template.getOrDefault("enabled", "false"))) {
                continue;
            }
            assertThat(template.get("message_template"))
                    .as("template %s", template.get("template_id"))
                    .doesNotContain("识别为")
                    .doesNotContain("意图")
                    .doesNotContain("ActionPlan")
                    .doesNotContain("JSON")
                    .doesNotContain("backend")
                    .doesNotContain("后端");
        }
    }

    @Test
    void atomicActionTemplatesCoverCurrentStorageAndShareCapabilities() {
        Map<String, Object> config = configLoader.loadJsonMap("rag/conversation/action_templates.json");
        Map<String, Object> actions = map(config, "actions");

        assertThat(config).containsEntry("version", "action_templates_v1");
        assertThat(actions.keySet()).contains(
                "folder.create",
                "file.upload",
                "node.rename",
                "node.move",
                "node.batch_move",
                "node.trash",
                "node.batch_trash",
                "share.create",
                "trash.restore"
        );

        Map<String, Object> upload = map(actions, "file.upload");
        assertThat(upload).containsEntry("executor", "client");
        assertThat(list(upload, "requiredClientFields")).containsExactly("files");

        Map<String, Object> permanentDelete = map(actions, "trash.delete_permanently");
        assertThat(permanentDelete).containsEntry("enabled", false);
        assertThat(permanentDelete).containsEntry("risk", "critical");
    }

    @Test
    void compositeTemplatesExpressMultiStepPlansWithoutHardCoding() {
        Map<String, Object> config = configLoader.loadJsonMap("rag/conversation/composite_actions.json");
        Map<String, Object> composites = map(config, "composites");

        assertThat(config).containsEntry("version", "composite_actions_v1");
        assertThat(composites.keySet()).contains(
                "composite.create_folder_then_upload",
                "composite.rename_then_share",
                "composite.move_then_share",
                "composite.restore_then_move"
        );

        Map<String, Object> createThenUpload = map(composites, "composite.create_folder_then_upload");
        List<Object> steps = list(createThenUpload, "steps");

        assertThat(createThenUpload).containsEntry("confirmationLevel", "conflict_then_final_review");
        assertThat(steps).hasSize(2);
        assertThat(map(steps.get(0))).containsEntry("action", "folder.create");
        assertThat(map(steps.get(1))).containsEntry("action", "file.upload");
    }

    @Test
    void collectionTemplatesCoverBatchFilterActions() {
        Map<String, Object> config = configLoader.loadJsonMap("rag/conversation/collection_actions.json");
        Map<String, Object> collections = map(config, "collections");

        assertThat(config).containsEntry("version", "collection_actions_v1");
        assertThat(collections.keySet()).contains(
                "collection.trash_by_name_contains",
                "collection.trash_by_category",
                "collection.move_by_extension",
                "collection.move_by_name_contains"
        );

        Map<String, Object> moveByExtension = map(collections, "collection.move_by_extension");
        Map<String, Object> sourceCollection = map(moveByExtension, "sourceCollection");
        Map<String, Object> filter = map(sourceCollection, "filter");

        assertThat(sourceCollection).containsEntry("requiresExactCount", true);
        assertThat(sourceCollection).containsEntry("requiresPreview", true);
        assertThat(filter).containsEntry("extension", "$entities.extension");
        assertThat(filter).containsEntry("includeFolders", false);
    }

    @Test
    void policiesAndDialogueTemplatesKeepRiskAndLocaleConfigurable() {
        Map<String, Object> policies = configLoader.loadJsonMap("rag/conversation/policies.json");
        Map<String, Object> dialogue = configLoader.loadJsonMap("rag/conversation/dialogue_templates.json");

        Map<String, Object> collectionPolicy = map(policies, "collectionPolicy");
        Map<String, Object> phasePolicy = map(policies, "phasePolicy");
        Map<String, Object> locales = map(dialogue, "locales");

        assertThat(policies).containsEntry("version", "action_policy_v1");
        assertThat(collectionPolicy).containsEntry("requiresExactCount", true);
        assertThat(collectionPolicy).containsEntry("requiresRevalidationBeforeExecute", true);
        assertThat(map(phasePolicy, "phase1")).containsEntry("enabled", true);
        assertThat(map(phasePolicy, "phase2")).containsEntry("enabled", false);

        assertThat(dialogue).containsEntry("version", "dialogue_templates_v1");
        assertThat(locales.keySet()).contains("zh-CN", "en-US");
        assertThat(map(locales, "zh-CN")).containsKeys(
                "candidate_selection_required",
                "collection_review_required",
                "conflict_resolution_required",
                "client_input_required"
        );
    }

    @Test
    void mobileContractDocumentsStableFieldsAndActionHandlerCoverage() {
        Map<String, Object> mobile = configLoader.loadJsonMap("rag/conversation/mobile_contract.json");
        Map<String, Object> bridge = configLoader.loadJsonMap("rag/conversation/action_bridge.json");
        Map<String, Object> actionHandlers = map(mobile, "actionHandlers");
        Map<String, Object> bridgeActions = map(bridge, "actions");

        assertThat(mobile).containsEntry("version", "mobile_contract_v1");
        assertThat(map(mobile, "encoding")).containsEntry("forbidNumericTypeCodes", true);
        assertThat(list(mobile, "stableResponseFields")).contains(
                "intentId",
                "nextAction",
                "candidateBinding",
                "actionPlan",
                "backendActionDraft",
                "assistantText",
                "conversation"
        );
        assertThat(map(mobile, "nextActionRouting").keySet()).contains(
                "respond_only",
                "ask_clarification",
                "wait_for_candidate_selection",
                "wait_for_user_confirmation",
                "handoff_to_backend",
                "handoff_to_client_upload"
        );
        assertThat(actionHandlers.keySet()).containsAll(bridgeActions.keySet());
        assertThat(map(actionHandlers, "rename"))
                .containsEntry("implementationStatus", "mobile_supported")
                .containsEntry("mobileRepositoryMethod", "renameNode");
        assertThat(map(actionHandlers, "delete")).containsEntry("mobileRepositoryMethod", "moveNodeToTrash");
        assertThat(map(actionHandlers, "collection.rename_add_prefix")).containsEntry("implementationStatus", "planning_only");
    }

    @Test
    void mobileAcceptanceScenariosStayAlignedWithConfiguredIntentSchema() {
        Map<String, Object> scenarios = configLoader.loadJsonMap("rag/conversation/acceptance_scenarios.json");
        Map<String, Object> intentsConfig = configLoader.loadJsonMap("rag/conversation/intents.json");
        Map<String, Object> schema = map(intentsConfig, "schema");

        List<String> intentIds = list(intentsConfig, "intents").stream()
                .map(ActionPlanContractConfigTest::map)
                .map(item -> String.valueOf(item.get("id")))
                .toList();
        List<Object> nextActions = list(schema, "allowed_next_actions");
        List<Object> risks = list(schema, "allowed_risks");
        List<Object> actionTypes = list(schema, "allowed_action_types");

        assertThat(scenarios).containsEntry("version", "rag_mobile_acceptance_v1");
        assertThat(list(scenarios, "automatedScenarios")).hasSizeGreaterThan(20);
        assertThat(list(scenarios, "manualScenarios")).hasSizeGreaterThanOrEqualTo(5);

        for (Object item : list(scenarios, "automatedScenarios")) {
            Map<String, Object> scenario = map(item);
            Map<String, Object> expected = map(scenario, "expected");
            assertThat(intentIds)
                    .as("scenario %s expected intent", scenario.get("id"))
                    .contains(String.valueOf(expected.get("intentId")));
            assertThat(nextActions)
                    .as("scenario %s expected nextAction", scenario.get("id"))
                    .contains(String.valueOf(expected.get("nextAction")));
            assertThat(risks)
                    .as("scenario %s expected risk", scenario.get("id"))
                    .contains(String.valueOf(expected.get("risk")));
            assertThat(actionTypes)
                    .as("scenario %s expected actionDraftType", scenario.get("id"))
                    .contains(String.valueOf(expected.get("actionDraftType")));
        }
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
