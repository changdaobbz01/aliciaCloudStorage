package com.alicia.cloudstorage.rag.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AssistantPlanStreamService {

    private static final long STREAM_TIMEOUT_MILLIS = 45_000L;
    private static final long TEXT_CHUNK_DELAY_MILLIS = 90L;

    private final AssistantConversationService conversationService;
    private final long heartbeatMillis;

    public AssistantPlanStreamService(
            AssistantConversationService conversationService,
            @Value("${alicia.rag.stream.heartbeat-millis:3000}") long heartbeatMillis
    ) {
        this.conversationService = conversationService;
        this.heartbeatMillis = Math.max(250L, heartbeatMillis);
    }

    public SseEmitter stream(AssistantPlanRequest request, String authorizationHeader) {
        return stream(request, authorizationHeader, new SseEmitter(STREAM_TIMEOUT_MILLIS));
    }

    SseEmitter stream(
            AssistantPlanRequest request,
            String authorizationHeader,
            SseEmitter emitter
    ) {
        Thread.ofVirtual().name("assistant-plan-stream").start(() -> run(emitter, request, authorizationHeader));
        return emitter;
    }

    private void run(SseEmitter emitter, AssistantPlanRequest request, String authorizationHeader) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            send(emitter, AssistantStreamEvent.status("安安正在理解你的意思..."));
            Future<IntentRecognitionResponse> future = executor.submit(() ->
                    conversationService.plan(request, authorizationHeader)
            );
            IntentRecognitionResponse response = waitForPlan(emitter, future);
            send(emitter, AssistantStreamEvent.status(statusText(response)));
            sleep(TEXT_CHUNK_DELAY_MILLIS);
            for (AssistantStreamEvent event : textChunks(response.assistantText())) {
                send(emitter, event);
                sleep(TEXT_CHUNK_DELAY_MILLIS);
            }
            send(emitter, AssistantStreamEvent.finalResponse(response));
            send(emitter, AssistantStreamEvent.done());
            emitter.complete();
        } catch (Exception error) {
            emitter.completeWithError(error);
        }
    }

    private IntentRecognitionResponse waitForPlan(
            SseEmitter emitter,
            Future<IntentRecognitionResponse> future
    ) throws Exception {
        while (true) {
            try {
                return future.get(heartbeatMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                send(emitter, AssistantStreamEvent.status("安安还在结合上下文认真理解，请稍等一下..."));
            }
        }
    }

    private void send(SseEmitter emitter, AssistantStreamEvent event) throws IOException {
        emitter.send(SseEmitter.event().name(event.type()).data(event));
    }

    String statusText(IntentRecognitionResponse response) {
        if (response == null || response.intentName() == null || response.intentName().isBlank()) {
            return "安安正在整理结果...";
        }
        return switch (response.nextAction()) {
            case "show_search_results" -> "安安已找到候选，正在展示结果...";
            case "ask_clarification" -> "安安还需要补充一点信息...";
            case "wait_for_user_confirmation" -> "安安正在生成需要确认的计划...";
            case "wait_for_backend_binding" -> "安安正在匹配云盘里的候选...";
            case "respond_only" -> "安安正在整理回复...";
            default -> "安安正在整理下一步...";
        };
    }

    private java.util.List<AssistantStreamEvent> textChunks(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<AssistantStreamEvent> events = new java.util.ArrayList<>();
        for (int index = 0; index < value.length(); index += 9) {
            events.add(AssistantStreamEvent.delta(value.substring(index, Math.min(value.length(), index + 9))));
        }
        return events;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("assistant stream interrupted", error);
        }
    }
}
