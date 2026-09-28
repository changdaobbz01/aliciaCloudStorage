package com.alicia.cloudstorage.ragexecution.api.publicapi;

import com.alicia.cloudstorage.ragexecution.application.ExecutionEventStreamService;
import com.alicia.cloudstorage.ragexecution.application.PublicExecutionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/executions")
public class PublicExecutionController {

    private final PublicExecutionService executionService;
    private final ExecutionEventStreamService streamService;

    public PublicExecutionController(
            PublicExecutionService executionService,
            ExecutionEventStreamService streamService
    ) {
        this.executionService = executionService;
        this.streamService = streamService;
    }

    @GetMapping("/{executionId}")
    public ExecutionResponse get(
            @PathVariable String executionId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return executionService.get(executionId, authorization);
    }

    @PostMapping("/{executionId}/confirm")
    public ExecutionResponse confirm(
            @PathVariable String executionId,
            @RequestBody ConfirmExecutionRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return executionService.confirm(executionId, request.expectedVersion(), authorization);
    }

    @PostMapping("/{executionId}/cancel")
    public ExecutionResponse cancel(
            @PathVariable String executionId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return executionService.cancel(executionId, authorization);
    }

    @PostMapping("/{executionId}/client-input")
    public ExecutionResponse completeClientInput(
            @PathVariable String executionId,
            @RequestBody CompleteClientInputRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return executionService.completeClientInput(executionId, request, authorization);
    }

    @GetMapping("/{executionId}/events")
    public List<ExecutionEventResponse> events(
            @PathVariable String executionId,
            @RequestParam(defaultValue = "0") long after,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return executionService.events(executionId, after, authorization);
    }

    @GetMapping(path = "/{executionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String executionId,
            @RequestParam(defaultValue = "0") long after,
            @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        long cursor = lastEventId == null ? after : Math.max(after, lastEventId);
        return streamService.open(executionId, cursor, authorization);
    }
}
