package com.alicia.cloudstorage.rag.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.UUID;

@Service
final class RecognitionFeedbackService {

    static final String HEADER = "X-Recognition-Feedback";
    private static final int CAPACITY = 256;

    private final boolean enabled;
    private final RecognitionTraceRecorder traces;
    private final CandidateSampleStore samples;
    private final LinkedHashMap<UUID, Feedback> reports = new LinkedHashMap<>();

    RecognitionFeedbackService(@Value("${alicia.rag.recognition.feedback.enabled:false}") boolean enabled,
                               RecognitionTraceRecorder traces, CandidateSampleStore samples) {
        this.enabled = enabled;
        this.traces = traces;
        this.samples = samples;
    }

    boolean enabled() {
        return enabled;
    }

    String mode() {
        return samples.enabled() ? "category,candidate" : "category";
    }

    synchronized void report(UUID traceId, Long userId, Category category) {
        requireOwnedTrace(traceId, userId);
        Feedback previous = reports.get(traceId);
        if (previous != null) {
            if (previous.category() != category) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "recognition_feedback_already_submitted");
            }
            return;
        }
        reports.put(traceId, new Feedback(category, null, null));
        if (reports.size() > CAPACITY) {
            reports.pollFirstEntry();
        }
    }

    synchronized String preview(UUID traceId, Long userId, String expression) {
        Feedback feedback = requireSampleFeedback(traceId, userId);
        if (feedback.candidateId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "candidate_already_submitted");
        }
        String cleaned = CandidateExpressionRedactor.preview(expression);
        reports.put(traceId, new Feedback(feedback.category(), digest(cleaned), null));
        return cleaned;
    }

    synchronized UUID saveCandidate(UUID traceId, Long userId, String expression, boolean consent) {
        Feedback feedback = requireSampleFeedback(traceId, userId);
        if (!consent || !CandidateExpressionRedactor.preview(expression).equals(expression)
                || !digest(expression).equals(feedback.previewDigest())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "candidate_review_required");
        }
        if (feedback.candidateId() != null) {
            return feedback.candidateId();
        }
        UUID candidateId = samples.save(feedback.category(), expression);
        reports.put(traceId, new Feedback(feedback.category(), feedback.previewDigest(), candidateId));
        return candidateId;
    }

    private void requireOwnedTrace(UUID traceId, Long userId) {
        if (!enabled || !traces.owns(traceId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "recognition_trace_unavailable");
        }
    }

    private Feedback requireSampleFeedback(UUID traceId, Long userId) {
        requireOwnedTrace(traceId, userId);
        if (!samples.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "candidate_collection_disabled");
        }
        Feedback feedback = reports.get(traceId);
        if (feedback == null || feedback.category() == Category.CORRECT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "error_feedback_required");
        }
        return feedback;
    }

    enum Category {
        CORRECT, WRONG_ACTION, WRONG_TARGET, WRONG_SCOPE, MISSED_CONTEXT, INCORRECT_REJECTION,
        CLARIFICATION_NEEDED, UNNATURAL_REPLY, OTHER
    }

    private static String digest(String expression) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(expression.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("Candidate review verification is unavailable.", error);
        }
    }

    private record Feedback(Category category, String previewDigest, UUID candidateId) {}
}
