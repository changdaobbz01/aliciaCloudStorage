package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionOperationSelectorResolverTest {

    private IntentRecognitionService intentRecognitionService;
    private CollectionOperationSelectorResolver resolver;

    @BeforeEach
    void setUp() {
        RagConfigLoader configLoader = new RagConfigLoader(new ObjectMapper());
        IntentRouter intentRouter = new IntentRouter(configLoader, new EntityExtractor(configLoader));
        intentRecognitionService = new IntentRecognitionService(
                message -> Optional.empty(),
                intentRouter,
                configLoader
        );
        resolver = new CollectionOperationSelectorResolver();
    }

    @Test
    void implicitDeleteCollectionRequiresExplicitQuantifier() {
        String message = "删除根目录下的文件";
        IntentRecognitionResponse response = resolver.apply(
                message,
                null,
                intentRecognitionService.recognizeLocal(message, "test")
        );

        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.reason()).contains("collection_quantifier_required");
        assertThat(response.assistantText()).contains("所有文件");
    }

    @Test
    void implicitMoveCollectionKeepsDestinationInClarification() {
        String message = "把测试目录下的文件移动到资料目录";
        IntentRecognitionResponse response = resolver.apply(
                message,
                null,
                intentRecognitionService.recognizeLocal(message, "test")
        );

        assertThat(response.nextAction()).isEqualTo("ask_clarification");
        assertThat(response.actionDraft().type()).isEqualTo("none");
        assertThat(response.assistantText()).contains("资料目录").contains("所有文件");
    }

    @Test
    void explicitAllStillBuildsSafeCollectionSelector() {
        String message = "删除根目录下的所有文件";
        IntentRecognitionResponse response = resolver.apply(
                message,
                null,
                intentRecognitionService.recognizeLocal(message, "test")
        );

        assertThat(response.actionDraft().type()).isEqualTo("collection.trash_scoped");
        assertThat(response.entities()).containsEntry("source_quantifier", "ALL");
        assertThat(response.safety().requiresConfirmation()).isTrue();
    }
}
