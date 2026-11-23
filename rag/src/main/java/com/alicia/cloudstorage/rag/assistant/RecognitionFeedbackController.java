package com.alicia.cloudstorage.rag.assistant;

import com.alicia.cloudstorage.rag.security.RagAccessAuthorizer;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/assistant/feedback", consumes = "application/json", produces = "application/json")
final class RecognitionFeedbackController {

    private final RagAccessAuthorizer authorizer;
    private final RecognitionFeedbackService service;

    RecognitionFeedbackController(RagAccessAuthorizer authorizer, RecognitionFeedbackService service) {
        this.authorizer = authorizer;
        this.service = service;
    }

    @PostMapping
    Map<String, Boolean> submit(@RequestHeader(value = "Authorization", required = false) String authorization,
                                @RequestBody Map<String, Object> body) {
        Long userId = authorizer.requireRagAccess(authorization).userId();
        requireFields(body, "traceId", "category");
        service.report(traceId(body), userId, category(body));
        return Map.of("accepted", true);
    }

    @PostMapping("/candidate/preview")
    Map<String, String> preview(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @RequestBody Map<String, Object> body) {
        Long userId = authorizer.requireRagAccess(authorization).userId();
        requireFields(body, "traceId", "expression");
        String cleaned = service.preview(traceId(body), userId, expression(body));
        return Map.of("expression", cleaned);
    }

    @PostMapping("/candidate")
    Map<String, UUID> saveCandidate(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestBody Map<String, Object> body) {
        Long userId = authorizer.requireRagAccess(authorization).userId();
        requireFields(body, "traceId", "expression", "consent");
        boolean consent = Boolean.TRUE.equals(body.get("consent"));
        UUID id = service.saveCandidate(traceId(body), userId, expression(body), consent);
        return Map.of("candidateId", id);
    }

    private static void requireFields(Map<String, Object> body, String... fields) {
        if (body == null || body.size() != fields.length || Arrays.stream(fields).anyMatch(field -> body.get(field) == null)) {
            throw invalidRequest();
        }
    }

    private static UUID traceId(Map<String, Object> body) {
        try {
            if (body.get("traceId") instanceof String raw && raw.length() == 36) {
                UUID parsed = UUID.fromString(raw);
                if (parsed.toString().equalsIgnoreCase(raw)) {
                    return parsed;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // A malformed trace ID is handled as a validation error.
        }
        throw invalidRequest();
    }

    private static RecognitionFeedbackService.Category category(Map<String, Object> body) {
        if (!(body.get("category") instanceof String value)) {
            throw invalidRequest();
        }
        try {
            return RecognitionFeedbackService.Category.valueOf(value);
        } catch (IllegalArgumentException error) {
            throw invalidRequest();
        }
    }

    private static String expression(Map<String, Object> body) {
        if (!(body.get("expression") instanceof String value)
                || value.isBlank() || value.length() > CandidateExpressionRedactor.MAX_LENGTH) {
            throw invalidRequest();
        }
        return value;
    }

    private static ResponseStatusException invalidRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_recognition_feedback");
    }
}
