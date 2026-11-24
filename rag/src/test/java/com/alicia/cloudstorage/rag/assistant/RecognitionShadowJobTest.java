package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RecognitionShadowJobTest {

    private AssistantSemanticOrchestrator recognizer;
    private DeepSeekIntentClient model;
    private RecognitionShadowJob job;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        RagConfigLoader configs = new RagConfigLoader(mapper);
        recognizer = mock(AssistantSemanticOrchestrator.class);
        model = mock(DeepSeekIntentClient.class);
        job = new RecognitionShadowJob(recognizer, model, new RecognitionTraceRecorder(true, configs), configs, mapper);
    }

    @Test
    void onlineModeWithNoModelFailsBeforeRecognition() {
        assertThatThrownBy(() -> job.run(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match the requested mode");
        verifyNoInteractions(recognizer);
    }

    @Test
    void anUnexpectedMutationDraftStopsTheBatchBeforeTheNextCase() {
        var response = mock(IntentRecognitionResponse.class);
        when(response.actionDraft()).thenReturn(new ActionDraft("folder.create", Map.of(), false));
        when(recognizer.decide(any(), any(), any(), any(), eq(false))).thenReturn(decision(response));

        assertThatThrownBy(() -> job.run(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Recognition shadow verification failed.");

        verify(recognizer).decide(any(), any(), any(), any(), eq(false));
        verifyNoMoreInteractions(recognizer);
    }

    @Test
    void onlineDownstreamFailureStopsWithoutRetrying() {
        when(model.isConfigured()).thenReturn(true);
        var response = mock(IntentRecognitionResponse.class);
        when(response.semanticFrame()).thenReturn(SemanticFrame.empty());
        when(recognizer.decide(any(), any(), any(), any(), eq(false))).thenAnswer(invocation -> {
            RecognitionTrace.modelCall(RecognitionTrace.Outcome.HTTP_ERROR, RecognitionTrace.tick(),
                    "deepseek-v4-flash", "semantic_frame_v2", 503);
            return decision(response);
        });

        assertThatThrownBy(() -> job.run(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Recognition shadow verification failed.");

        verify(recognizer).decide(any(), any(), any(), any(), eq(false));
        verifyNoMoreInteractions(recognizer);
    }

    private AssistantSemanticOrchestrator.Decision decision(IntentRecognitionResponse response) {
        return new AssistantSemanticOrchestrator.Decision(
                response, AssistantSemanticOrchestrator.Kind.CURRENT_TURN, null, null, false
        );
    }
}
