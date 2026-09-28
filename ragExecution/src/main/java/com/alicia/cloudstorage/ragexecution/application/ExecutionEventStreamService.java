package com.alicia.cloudstorage.ragexecution.application;

import com.alicia.cloudstorage.ragexecution.api.publicapi.ExecutionEventResponse;
import com.alicia.cloudstorage.ragexecution.config.RagExecutionWorkerProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ExecutionEventStreamService {

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "SUCCEEDED", "PARTIALLY_SUCCEEDED", "FAILED", "CANCELLED", "EXPIRED"
    );

    private final PublicExecutionService executionService;
    private final RagExecutionWorkerProperties properties;
    private final ExecutorService executor;

    public ExecutionEventStreamService(
            PublicExecutionService executionService,
            RagExecutionWorkerProperties properties,
            @Qualifier("executionStreamExecutor") ExecutorService executor
    ) {
        this.executionService = executionService;
        this.properties = properties;
        this.executor = executor;
    }

    public SseEmitter open(String executionId, long after, String authorization) {
        if (after < 0) {
            throw new ExecutionAccessException(400, "invalid_event_sequence");
        }
        executionService.authorizeStream(executionId, authorization);
        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(ignored -> closed.set(true));
        executor.execute(() -> stream(executionId, after, emitter, closed));
        return emitter;
    }

    private void stream(String executionId, long after, SseEmitter emitter, AtomicBoolean closed) {
        long cursor = after;
        long deadline = System.nanoTime() + properties.streamTimeout().toNanos();
        try {
            while (!closed.get() && System.nanoTime() < deadline) {
                List<ExecutionEventResponse> events = executionService.eventsForOwnedExecution(executionId, cursor);
                boolean terminal = false;
                for (ExecutionEventResponse event : events) {
                    emitter.send(SseEmitter.event()
                            .id(Long.toString(event.sequence()))
                            .name(event.type())
                            .data(event));
                    cursor = event.sequence();
                    terminal = isTerminal(event);
                }
                if (terminal) {
                    emitter.complete();
                    return;
                }
                if (events.isEmpty()) {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                }
                sleep(properties.streamPollInterval());
            }
            if (!closed.get()) {
                emitter.complete();
            }
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (RuntimeException exception) {
            emitter.completeWithError(exception);
        }
    }

    private boolean isTerminal(ExecutionEventResponse event) {
        if (!(event.payload() instanceof java.util.Map<?, ?> payload)) {
            return false;
        }
        return TERMINAL_STATUSES.contains(String.valueOf(payload.get("status")));
    }

    private void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
    }
}
