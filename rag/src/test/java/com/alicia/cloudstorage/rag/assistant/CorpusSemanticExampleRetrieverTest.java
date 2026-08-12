package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CorpusSemanticExampleRetrieverTest {

    private final RagConfigLoader configLoader = new RagConfigLoader(new ObjectMapper());
    private final CorpusSemanticExampleRetriever retriever = new CorpusSemanticExampleRetriever(configLoader);

    @Test
    void loadsCleanedRuntimeCorpusAndRetrievesRenameExamples() {
        List<SemanticExampleRetriever.SemanticExample> examples = retriever.retrieve(
                "把合同.docx改名为最终合同.docx",
                5
        );

        assertThat(retriever.size()).isEqualTo(338);
        assertThat(examples).isNotEmpty();
        assertThat(examples).extracting(SemanticExampleRetriever.SemanticExample::intentId)
                .contains("file_rename");
    }

    @Test
    void unsupportedCompositeRuleIsRetrievedAsCapabilityBoundary() {
        List<SemanticExampleRetriever.SemanticBoundary> boundaries = retriever.retrieveBoundaries(
                "如果/云盘/图片不存在先建一个，再把图片放进去",
                3
        );

        assertThat(retriever.boundarySize()).isEqualTo(449);
        assertThat(boundaries).isNotEmpty();
        assertThat(boundaries.get(0).score()).isGreaterThan(0.58);
    }

    @Test
    void capabilityQuestionStaysNonMutating() {
        List<SemanticExampleRetriever.SemanticExample> examples = retriever.retrieve(
                "可以批量上传文件夹吗？",
                5
        );

        assertThat(examples).extracting(SemanticExampleRetriever.SemanticExample::intentId)
                .contains("assistant_capability_examples");
    }

    @Test
    void runtimeAndHeldOutRecognitionSetsDoNotOverlap() {
        JsonNode runtime = configLoader.loadJson("rag/corpus/cloud_drive_semantic_examples.json");
        JsonNode evaluation = configLoader.loadJson("rag/corpus/cloud_drive_semantic_eval.json");
        Set<String> runtimeIds = ids(runtime.path("examples"));
        Set<String> evaluationIds = ids(evaluation.path("examples"));

        assertThat(runtimeIds).hasSize(338);
        assertThat(runtimeIds).doesNotContainAnyElementsOf(evaluationIds);
        assertThat(evaluationIds).hasSize(531);
    }

    private Set<String> ids(JsonNode examples) {
        Set<String> values = new HashSet<>();
        examples.forEach(example -> values.add(example.path("id").asText()));
        return values;
    }
}
