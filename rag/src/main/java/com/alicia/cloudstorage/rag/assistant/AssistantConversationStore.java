package com.alicia.cloudstorage.rag.assistant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AssistantConversationStore {

    private final AssistantConversationStateRepository repository;
    private final RedisConversationStateRepository persistent;
    private final Duration ttl;
    private final int maxConversations;

    @Autowired
    public AssistantConversationStore(
            AssistantConversationStateRepository repository,
            @Value("${alicia.rag.conversation.ttl-minutes:30}") long ttlMinutes,
            @Value("${alicia.rag.conversation.max-conversations:500}") int maxConversations,
            RedisConversationStateRepository persistent
    ) {
        this.repository = repository;
        this.persistent = persistent;
        this.ttl = Duration.ofMinutes(persistent.mode() == RedisConversationStateRepository.Mode.MEMORY
                ? Math.max(1L, ttlMinutes) : Math.clamp(ttlMinutes, 1, 120));
        this.maxConversations = Math.max(32, maxConversations);
    }

    public AssistantConversationStore(long ttlMinutes, int maxConversations) {
        this(new InMemoryAssistantConversationStateRepository(), ttlMinutes, maxConversations,
                RedisConversationStateRepository.inMemoryOnly());
    }

    public AssistantConversationState resolve(String requestedConversationId) {
        return resolve(requestedConversationId, "");
    }

    public AssistantConversationState resolve(String requestedConversationId, String authorizationHeader) {
        return resolve(requestedConversationId, authorizationHeader, null);
    }

    AssistantConversationState resolve(String requestedConversationId, String authorizationHeader, Long owner) {
        long started = RecognitionTrace.tick();
        Instant now = Instant.now();
        if (usesRedis()) {
            String id = persistedId(requestedConversationId);
            if (validOwner(owner) && !id.isBlank()) {
                try {
                    var existing = persistent.find(owner, id).filter(state -> !state.isExpired(now));
                    if (existing.isPresent()) {
                        RecognitionTrace.session(RecognitionTrace.Outcome.RESTORED, started, existing.get());
                        return existing.get();
                    }
                } catch (ConversationPersistenceUnavailableException error) {
                    RecognitionTrace.mark(RecognitionTrace.Stage.SESSION_STORAGE, RecognitionTrace.Outcome.UNAVAILABLE, started);
                }
            }
            AssistantConversationState created = newConversation(now, "");
            RecognitionTrace.session(RecognitionTrace.Outcome.CREATED, started, created);
            return created;
        }
        purgeExpired(now);
        String fingerprint = authorizationFingerprint(authorizationHeader);
        String conversationId = normalizeConversationId(requestedConversationId);
        if (!conversationId.isBlank()) {
            AssistantConversationState existing = repository.find(conversationId).orElse(null);
            if (existing != null
                    && !existing.isExpired(now)
                    && existing.authorizationFingerprint().equals(fingerprint)) {
                if (usesShadow() && validOwner(owner)) {
                    observeShadow(owner, existing);
                }
                RecognitionTrace.session(RecognitionTrace.Outcome.RESTORED, started, existing);
                return existing;
            }
        }

        AssistantConversationState created = newConversation(now, fingerprint);
        RecognitionTrace.session(RecognitionTrace.Outcome.CREATED, started, created);
        return created;
    }

    public AssistantConversationState save(
            AssistantConversationState previous,
            IntentRecognitionResponse response
    ) {
        return save(previous, response, response.message(), null);
    }

    AssistantConversationState save(
            AssistantConversationState previous, IntentRecognitionResponse response, String userMessage, Long owner
    ) {
        Instant now = Instant.now();
        List<AssistantConversationTurn> history = persistent.mode() != RedisConversationStateRepository.Mode.MEMORY && validOwner(owner)
                ? AssistantConversationTurn.append(previous.recentTurns(), userMessage, response.assistantText())
                : List.of();
        AssistantConversationState next = new AssistantConversationState(
                previous.conversationId(),
                previous.turnIndex() + 1,
                response.intentId(),
                response.entities(),
                response.missingSlots(),
                response.actionDraft(),
                response.actionPlan(),
                response.candidateBinding(),
                AssistantConversationFocus.next(previous.focus(), response),
                response.semanticFrame(),
                previous.authorizationFingerprint(),
                now.plus(ttl),
                history
        );
        if (usesRedis()) {
            if (validOwner(owner)) {
                storePersisted(owner, previous, next, now);
            }
            return next;
        }
        purgeExpired(now);
        trimIfNeeded();
        repository.save(next);
        if (usesShadow() && validOwner(owner)) {
            long started = RecognitionTrace.tick();
            try {
                boolean saved = persistent.save(owner, previous.turnIndex(), next, now);
                RecognitionTrace.mark(RecognitionTrace.Stage.SESSION_STORAGE,
                        saved ? RecognitionTrace.Outcome.SUCCESS : RecognitionTrace.Outcome.NO_MATCH, started);
            } catch (ConversationPersistenceUnavailableException error) {
                RecognitionTrace.mark(RecognitionTrace.Stage.SESSION_STORAGE, RecognitionTrace.Outcome.UNAVAILABLE, started);
            }
        }
        return next;
    }

    private void storePersisted(long owner, AssistantConversationState previous, AssistantConversationState next, Instant now) {
        long started = RecognitionTrace.tick();
        try {
            if (!persistent.save(owner, previous.turnIndex(), next, now)) {
                RecognitionTrace.mark(RecognitionTrace.Stage.SESSION_STORAGE, RecognitionTrace.Outcome.NO_MATCH, started);
                throw new ConversationContinuityException(HttpStatus.CONFLICT, "conversation_turn_conflict",
                        "对话有新变化，请重新发送这一条。");
            }
        } catch (ConversationPersistenceUnavailableException error) {
            RecognitionTrace.mark(RecognitionTrace.Stage.SESSION_STORAGE, RecognitionTrace.Outcome.UNAVAILABLE, started);
            if (previous.turnIndex() > 0) {
                throw continuityUnavailable();
            }
        }
    }

    public AssistantConversationState restart() {
        return restart("");
    }

    public AssistantConversationState restart(String authorizationHeader) {
        return newConversation(Instant.now(), usesRedis() ? "" : authorizationFingerprint(authorizationHeader));
    }

    AssistantConversationState reset(String conversationId, String authorizationHeader, Long owner) {
        if (usesRedis() || usesShadow()) {
            complete(conversationId, authorizationHeader, owner);
        }
        return restart(authorizationHeader);
    }

    public void complete(String conversationId) {
        complete(conversationId, "");
    }

    public void complete(String conversationId, String authorizationHeader) {
        complete(conversationId, authorizationHeader, null);
    }

    void complete(String conversationId, String authorizationHeader, Long owner) {
        if ((usesRedis() || usesShadow()) && validOwner(owner)) {
            String id = persistedId(conversationId);
            if (!id.isBlank()) {
                try {
                    persistent.delete(owner, id);
                } catch (ConversationPersistenceUnavailableException error) {
                    if (usesRedis()) {
                        throw continuityUnavailable();
                    }
                }
            }
        }
        if (usesRedis()) {
            return;
        }
        String normalized = normalizeConversationId(conversationId);
        AssistantConversationState existing = normalized.isBlank()
                ? null
                : repository.find(normalized).orElse(null);
        if (existing != null
                && existing.authorizationFingerprint().equals(authorizationFingerprint(authorizationHeader))) {
            repository.delete(normalized);
        }
    }

    private AssistantConversationState newConversation(Instant now, String authorizationFingerprint) {
        return new AssistantConversationState(
                UUID.randomUUID().toString(),
                0,
                "",
                Map.of(),
                java.util.List.of(),
                null,
                null,
                null,
                AssistantConversationFocus.empty(),
                SemanticFrame.empty(),
                authorizationFingerprint,
                now.plus(ttl)
        );
    }

    private String normalizeConversationId(String conversationId) {
        String value = conversationId == null ? "" : conversationId.trim();
        if (value.length() > 128 || !value.matches("[A-Za-z0-9._-]*")) {
            return "";
        }
        return value;
    }

    private static String persistedId(String conversationId) {
        String value = conversationId == null ? "" : conversationId.trim();
        if (value.length() == 36) {
            try {
                String canonical = UUID.fromString(value).toString();
                if (canonical.equalsIgnoreCase(value)) {
                    return canonical;
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid conversation IDs are treated as a new conversation.
            }
        }
        return "";
    }

    private void observeShadow(long owner, AssistantConversationState expected) {
        long started = RecognitionTrace.tick();
        try {
            boolean same = persistent.matches(owner, expected);
            RecognitionTrace.mark(RecognitionTrace.Stage.SESSION_STORAGE,
                    same ? RecognitionTrace.Outcome.OBSERVED : RecognitionTrace.Outcome.NO_MATCH, started);
        } catch (ConversationPersistenceUnavailableException error) {
            RecognitionTrace.mark(RecognitionTrace.Stage.SESSION_STORAGE, RecognitionTrace.Outcome.UNAVAILABLE, started);
        }
    }

    private boolean usesRedis() {
        return persistent.mode() == RedisConversationStateRepository.Mode.REDIS;
    }

    private boolean usesShadow() {
        return persistent.mode() == RedisConversationStateRepository.Mode.SHADOW;
    }

    private static boolean validOwner(Long owner) {
        return owner != null && owner > 0;
    }

    private static ConversationContinuityException continuityUnavailable() {
        return new ConversationContinuityException(HttpStatus.SERVICE_UNAVAILABLE, "conversation_persistence_unavailable",
                "暂时无法保存旧会话，请稍后重试。");
    }

    static final class ConversationContinuityException extends ResponseStatusException {
        private final String clientMessage;

        ConversationContinuityException(HttpStatus status, String code, String clientMessage) {
            super(status, code);
            this.clientMessage = clientMessage;
        }

        String clientMessage() {
            return clientMessage;
        }
    }

    private void purgeExpired(Instant now) {
        repository.purgeExpired(now);
    }

    private void trimIfNeeded() {
        repository.trimToSize(maxConversations);
    }

    private String authorizationFingerprint(String authorizationHeader) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((authorizationHeader == null ? "" : authorizationHeader.trim())
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint authorization context.", exception);
        }
    }
}
