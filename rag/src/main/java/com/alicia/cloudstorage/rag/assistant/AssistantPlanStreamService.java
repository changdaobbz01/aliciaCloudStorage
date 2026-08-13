package com.alicia.cloudstorage.rag.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AssistantPlanStreamService {

    private static final long DEFAULT_STREAM_TIMEOUT_MILLIS = 60_000L;
    private static final long DEFAULT_PLAN_TIMEOUT_MILLIS = 35_000L;
    private static final long TEXT_CHUNK_DELAY_MILLIS = 90L;
    private static final String PLAN_TIMEOUT_MESSAGE =
            "这次处理超过了安全等待时间，我已经停止本次请求。请稍后再试一次。";

    private final AssistantConversationService conversationService;
    private final long heartbeatMillis;
    private final long streamTimeoutMillis;
    private final long planTimeoutMillis;

    @Autowired
    public AssistantPlanStreamService(
            AssistantConversationService conversationService,
            @Value("${alicia.rag.stream.heartbeat-millis:3000}") long heartbeatMillis,
            @Value("${alicia.rag.stream.timeout-millis:60000}") long streamTimeoutMillis,
            @Value("${alicia.rag.stream.plan-timeout-millis:35000}") long planTimeoutMillis
    ) {
        this.conversationService = conversationService;
        this.heartbeatMillis = Math.max(10L, heartbeatMillis);
        this.streamTimeoutMillis = Math.max(500L, streamTimeoutMillis);
        this.planTimeoutMillis = Math.min(
                Math.max(this.heartbeatMillis, planTimeoutMillis),
                this.streamTimeoutMillis - 100L
        );
    }

    AssistantPlanStreamService(
            AssistantConversationService conversationService,
            long heartbeatMillis
    ) {
        this(
                conversationService,
                heartbeatMillis,
                DEFAULT_STREAM_TIMEOUT_MILLIS,
                DEFAULT_PLAN_TIMEOUT_MILLIS
        );
    }

    public SseEmitter stream(AssistantPlanRequest request, String authorizationHeader) {
        return stream(request, authorizationHeader, new SseEmitter(streamTimeoutMillis));
    }

    SseEmitter stream(
            AssistantPlanRequest request,
            String authorizationHeader,
            SseEmitter emitter
    ) {
        StreamSession session = new StreamSession(emitter);
        emitter.onCompletion(session::close);
        emitter.onTimeout(session::cancel);
        emitter.onError(ignored -> session.cancel());
        Thread worker = Thread.ofVirtual()
                .name("assistant-plan-stream")
                .start(() -> run(session, request, authorizationHeader));
        session.worker(worker);
        return emitter;
    }

    private void run(StreamSession session, AssistantPlanRequest request, String authorizationHeader) {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            send(session, AssistantStreamEvent.status("安安正在理解你的意思..."));
            Future<IntentRecognitionResponse> future = executor.submit(() ->
                    conversationService.plan(request, authorizationHeader)
            );
            session.plan(future);
            IntentRecognitionResponse response = waitForPlan(session, future);
            send(session, AssistantStreamEvent.status(statusText(response)));
            sleep(TEXT_CHUNK_DELAY_MILLIS);
            for (AssistantStreamEvent event : textChunks(response.assistantText())) {
                send(session, event);
                sleep(TEXT_CHUNK_DELAY_MILLIS);
            }
            send(session, AssistantStreamEvent.finalResponse(response));
            send(session, AssistantStreamEvent.done());
            session.complete();
        } catch (TimeoutException error) {
            session.cancelPlan();
            sendIfOpen(session, AssistantStreamEvent.error(PLAN_TIMEOUT_MESSAGE));
            sendIfOpen(session, AssistantStreamEvent.done());
            session.complete();
        } catch (CancellationException ignored) {
            session.close();
        } catch (Exception error) {
            session.cancelPlan();
            session.completeWithError(error);
        } finally {
            executor.shutdownNow();
        }
    }

    private IntentRecognitionResponse waitForPlan(
            StreamSession session,
            Future<IntentRecognitionResponse> future
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(planTimeoutMillis);
        while (true) {
            if (!session.isOpen()) {
                throw new CancellationException("assistant stream closed");
            }
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
            if (remainingMillis <= 0L) {
                throw new TimeoutException("assistant plan timed out");
            }
            try {
                return future.get(Math.min(heartbeatMillis, remainingMillis), TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                if (System.nanoTime() >= deadlineNanos) {
                    throw new TimeoutException("assistant plan timed out");
                }
                send(session, AssistantStreamEvent.status("安安还在结合上下文认真理解，请稍等一下..."));
            }
        }
    }

    private void send(StreamSession session, AssistantStreamEvent event) throws IOException {
        if (!session.isOpen()) {
            throw new CancellationException("assistant stream closed");
        }
        session.emitter().send(SseEmitter.event().name(event.type()).data(event));
    }

    private void sendIfOpen(StreamSession session, AssistantStreamEvent event) {
        if (!session.isOpen()) {
            return;
        }
        try {
            send(session, event);
        } catch (Exception ignored) {
            session.cancel();
        }
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

    private static final class StreamSession {
        private final SseEmitter emitter;
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicReference<Future<?>> plan = new AtomicReference<>();
        private final AtomicReference<Thread> worker = new AtomicReference<>();

        private StreamSession(SseEmitter emitter) {
            this.emitter = emitter;
        }

        private SseEmitter emitter() {
            return emitter;
        }

        private boolean isOpen() {
            return open.get();
        }

        private void plan(Future<?> future) {
            plan.set(future);
            if (!isOpen()) {
                future.cancel(true);
            }
        }

        private void worker(Thread thread) {
            worker.set(thread);
            if (!isOpen()) {
                thread.interrupt();
            }
        }

        private void cancelPlan() {
            Future<?> future = plan.getAndSet(null);
            if (future != null) {
                future.cancel(true);
            }
        }

        private void cancel() {
            if (open.compareAndSet(true, false)) {
                cancelPlan();
                Thread thread = worker.get();
                if (thread != null && thread != Thread.currentThread()) {
                    thread.interrupt();
                }
                emitter.complete();
            }
        }

        private void close() {
            open.set(false);
            cancelPlan();
        }

        private void complete() {
            if (open.compareAndSet(true, false)) {
                cancelPlan();
                emitter.complete();
            }
        }

        private void completeWithError(Throwable error) {
            if (open.compareAndSet(true, false)) {
                cancelPlan();
                emitter.completeWithError(error);
            }
        }
    }
}
