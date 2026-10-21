package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssistantPlanRequestValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private AssistantConversationService conversationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        conversationService = mock(AssistantConversationService.class);
        AssistantController controller = new AssistantController(
                conversationService,
                new RagConfigLoader(objectMapper),
                objectMapper
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/assistant/plan",
            "/api/assistant/plan/stream",
            "/api/intent/recognize"
    })
    void rejectsOversizedMessagesBeforePlanning(String endpoint) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "message", "x".repeat(4097),
                "conversationId", "conversation-1"
        ));

        mockMvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(conversationService);
    }

    @Test
    void validatesConversationAndNestedClientContextBounds() {
        AssistantPlanRequest request = new AssistantPlanRequest(
                "create a folder",
                "invalid conversation id",
                new AssistantClientContext(
                        -1L,
                        "/" + "a".repeat(2048),
                        Map.of("files", 501),
                        "v".repeat(65),
                        List.of("a".repeat(97))
                ),
                new AssistantClientEvent(
                        "T".repeat(65),
                        0L,
                        501,
                        "b".repeat(129),
                        "p".repeat(129),
                        "o".repeat(65)
                )
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(
                        "conversationId",
                        "clientContext.currentFolderId",
                        "clientContext.currentFolderPath",
                        "clientContext.availableClientInputs[files].<map value>",
                        "clientContext.actionContractVersion",
                        "clientContext.supportedActionTypes[0].<list element>",
                        "clientEvent.type",
                        "clientEvent.candidateId",
                        "clientEvent.candidateIndex",
                        "clientEvent.bindingKey",
                        "clientEvent.planId",
                        "clientEvent.outcome"
                );
    }

    @Test
    void acceptsTheDocumentedMobileContractShape() {
        AssistantPlanRequest request = new AssistantPlanRequest(
                "create a folder named reports",
                "conversation-1",
                new AssistantClientContext(
                        7L,
                        "/documents",
                        Map.of("files", 2),
                        "action_bridge_v2",
                        List.of("folder.create", "rename")
                ),
                new AssistantClientEvent("SELECT_CANDIDATE", 11L, 1, "target", "plan-1", "selected")
        );

        assertThat(validator.validate(request)).isEmpty();
    }
}
