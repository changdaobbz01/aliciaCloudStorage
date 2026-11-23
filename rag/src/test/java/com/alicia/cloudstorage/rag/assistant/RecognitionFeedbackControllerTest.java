package com.alicia.cloudstorage.rag.assistant;

import com.alicia.cloudstorage.rag.security.RagAccessPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecognitionFeedbackControllerTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final RecognitionTraceRecorder traces = new RecognitionTraceRecorder(true, new RagConfigLoader(json));
    private static final String FEEDBACK = "/api/assistant/feedback";
    private static final String PREVIEW = FEEDBACK + "/candidate/preview";
    private static final String CANDIDATE = FEEDBACK + "/candidate";
    private static final String OWNER = "Bearer sample-owner";
    private static final String OTHER = "Bearer other-user";

    @TempDir
    Path candidateDirectory;

    private MockMvc web;
    private UUID traceId;

    @BeforeEach
    void setUp() throws Exception {
        if (Files.getFileStore(candidateDirectory).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(candidateDirectory, PosixFilePermissions.fromString("rwx------"));
        }
        web = mvc(true, candidateDirectory.toString());
        var trace = traces.start(RecognitionTrace.Route.PLAN, 27L);
        trace.finish(RecognitionTrace.Outcome.SUCCESS);
        traceId = trace.id();
    }

    @Test
    void categoryOnlyRequestIsAuthenticatedOwnedStrictAndIdempotent() throws Exception {
        var category = Map.of("traceId", traceId, "category", "WRONG_ACTION");
        web.perform(request(FEEDBACK, category)).andExpect(status().isUnauthorized());
        web.perform(request(FEEDBACK, category).header("Authorization", OTHER)).andExpect(status().isNotFound());
        web.perform(request(FEEDBACK, Map.of("traceId", traceId, "category", "WRONG_ACTION", "message", "private"))
                        .header("Authorization", OWNER))
                .andExpect(status().isBadRequest());
        web.perform(request(FEEDBACK, Map.of("traceId", traceId, "category", "unknown")).header("Authorization", OWNER))
                .andExpect(status().isBadRequest());
        web.perform(request(FEEDBACK, category).header("Authorization", OWNER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true));
        web.perform(request(FEEDBACK, category).header("Authorization", "Bearer refreshed-owner"))
                .andExpect(status().isOk());
        web.perform(request(FEEDBACK, Map.of("traceId", traceId, "category", "CORRECT")).header("Authorization", OWNER))
                .andExpect(status().isConflict());
        assertNoSamples();
    }

    @Test
    void sampleRequiresErrorFeedbackReviewedPreviewAndIndependentConsent() throws Exception {
        String expression = "帮我查找 \"private-filename.pdf\"，路径 C:\\docs\\private-path，链接 "
                + "https://example.test/private?signature=secret，邮箱 alice@example.test，"
                + "Bearer private-token，nodeId=123456，手机号13800138000";
        String anonymized = "帮我查找【文件】";
        web.perform(request(PREVIEW, Map.of("traceId", traceId, "expression", expression)).header("Authorization", OWNER))
                .andExpect(status().isConflict());
        web.perform(request(FEEDBACK, Map.of("traceId", traceId, "category", "WRONG_TARGET")).header("Authorization", OWNER))
                .andExpect(status().isOk());

        web.perform(request(CANDIDATE, candidate(anonymized, true)).header("Authorization", OWNER))
                .andExpect(status().isUnprocessableContent());
        String previewResult = web.perform(request(PREVIEW, Map.of("traceId", traceId, "expression", expression))
                        .header("Authorization", OWNER))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String scrubbed = json.readTree(previewResult).path("expression").asText();
        assertThat(scrubbed).contains("帮我查找", "【名称】", "【路径】", "【链接】", "【邮箱】", "【凭据】", "【编号】", "【手机号】")
                .doesNotContain("private-filename", "private-path", "example.test", "private-token", "123456", "13800138000");
        assertNoSamples();

        web.perform(request(CANDIDATE, candidate(scrubbed, false)).header("Authorization", OWNER))
                .andExpect(status().isUnprocessableContent());
        web.perform(request(CANDIDATE, candidate(anonymized, true)).header("Authorization", OWNER))
                .andExpect(status().isUnprocessableContent());
        web.perform(request(CANDIDATE, candidate(scrubbed, true)).header("Authorization", OTHER))
                .andExpect(status().isNotFound());
        assertNoSamples();

        var save = candidate(scrubbed, true);
        String receipt = web.perform(request(CANDIDATE, save).header("Authorization", OWNER))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(receipt).path("candidateId").asText());
        web.perform(request(CANDIDATE, save).header("Authorization", OWNER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.candidateId").value(id.toString()));
        try (var entries = Files.list(candidateDirectory)) {
            assertThat(entries.toList()).containsExactly(candidateDirectory.resolve("candidate-" + id + ".json"));
        }
        Path file = candidateDirectory.resolve("candidate-" + id + ".json");
        JsonNode stored = json.readTree(Files.readString(file));
        assertThat(stored.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "split", "category", "utterance", "consentVersion", "createdAt");
        assertThat(stored.path("id").asText()).isNotEqualTo(traceId.toString());
        assertThat(stored.path("split").asText()).isEqualTo("candidate");
        assertThat(stored.path("utterance").asText()).isEqualTo(scrubbed);
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void distinguishesDisabledCollectionFromCategoryAndRefusesCorrectSamples() throws Exception {
        var correct = Map.of("traceId", traceId, "category", "CORRECT");
        mvc(false, candidateDirectory.toString()).perform(request(FEEDBACK, correct).header("Authorization", OWNER))
                .andExpect(status().isNotFound());

        var categoryOnly = mvc(true, "");
        categoryOnly.perform(request(FEEDBACK, correct).header("Authorization", OWNER)).andExpect(status().isOk());
        categoryOnly.perform(request(PREVIEW, Map.of("traceId", traceId, "expression", "请查找【文件】"))
                        .header("Authorization", OWNER))
                .andExpect(status().isNotFound());
        web.perform(request(FEEDBACK, correct).header("Authorization", OWNER)).andExpect(status().isOk());
        web.perform(request(PREVIEW, Map.of("traceId", traceId, "expression", "请查找【文件】"))
                        .header("Authorization", OWNER))
                .andExpect(status().isConflict());
        assertNoSamples();
    }

    private MockMvc mvc(boolean enabled, String directory) {
        var feedback = new RecognitionFeedbackService(enabled, traces, new CandidateSampleStore(directory, json));
        return MockMvcBuilders.standaloneSetup(new RecognitionFeedbackController(authorization -> {
            if (OWNER.equals(authorization) || "Bearer refreshed-owner".equals(authorization)) {
                return new RagAccessPrincipal(27L, "rag", "RAG_USER");
            }
            if (OTHER.equals(authorization)) {
                return new RagAccessPrincipal(28L, "rag", "RAG_ADMIN");
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }, feedback)).build();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String path, Object body)
            throws Exception {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
    }

    private Map<String, Object> candidate(String expression, boolean consent) {
        return Map.of("traceId", traceId, "expression", expression, "consent", consent);
    }

    private void assertNoSamples() throws Exception {
        try (var entries = Files.list(candidateDirectory)) {
            assertThat(entries.toList()).isEmpty();
        }
    }
}
