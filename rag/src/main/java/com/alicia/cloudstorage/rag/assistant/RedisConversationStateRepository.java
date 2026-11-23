package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Component
final class RedisConversationStateRepository implements AutoCloseable {

    enum Mode { MEMORY, SHADOW, REDIS }

    private static final String CAS = """
            local turn = redis.call('HGET', KEYS[1], 'turn')
            if ARGV[1] == '0' then
                if redis.call('EXISTS', KEYS[1]) ~= 0 then return 0 end
            elseif turn ~= ARGV[1] then
                return 0
            end
            redis.call('HSET', KEYS[1], 'turn', ARGV[2], 'payload', ARGV[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            return 1
            """;

    private final Mode mode;
    private final EncryptedConversationCodec codec;
    private final RedisClient client;
    private StatefulRedisConnection<String, String> connection;
    private long retryConnectAtNanos;

    @Autowired
    RedisConversationStateRepository(
            @Value("${alicia.rag.conversation.persistence-mode:MEMORY}") Mode mode,
            @Value("${alicia.rag.conversation.redis-url:}") String url,
            @Value("${alicia.rag.conversation.redis-key:}") String key,
            @Value("${alicia.rag.conversation.redis-timeout-ms:600}") long timeoutMillis,
            ObjectMapper json
    ) {
        this.mode = mode;
        if (mode == Mode.MEMORY) {
            this.codec = null;
            this.client = null;
            return;
        }
        this.codec = new EncryptedConversationCodec(key, json);
        Duration timeout = Duration.ofMillis(Math.clamp(timeoutMillis, 100, 2_000));
        this.client = RedisClient.create(endpoint(url, timeout));
        this.client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(timeout).build())
                .timeoutOptions(TimeoutOptions.enabled(timeout))
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
    }

    static RedisConversationStateRepository inMemoryOnly() {
        return new RedisConversationStateRepository(Mode.MEMORY, "", "", 600, null);
    }

    Mode mode() {
        return mode;
    }

    Optional<AssistantConversationState> find(long owner, String id) {
        String key = codec.key(owner, id);
        Map<String, String> fields = redis(commands -> commands.hgetall(key));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        AssistantConversationState value = codec.decode(key, fields.get("payload"));
        if (value == null || !id.equals(value.conversationId()) || value.turnIndex() <= 0
                || !Integer.toString(value.turnIndex()).equals(fields.get("turn"))) {
            throw new ConversationPersistenceUnavailableException();
        }
        return Optional.of(value);
    }

    boolean matches(long owner, AssistantConversationState state) {
        return find(owner, state.conversationId()).map(value -> codec.sameState(state, value)).orElse(false);
    }

    boolean save(long owner, int expectedTurn, AssistantConversationState next, Instant now) {
        String key = codec.key(owner, next.conversationId());
        String payload = codec.encode(key, next);
        long ttlMillis = Math.max(1, Duration.between(now, next.expiresAt()).toMillis());
        Long applied = redis(commands -> commands.eval(CAS, ScriptOutputType.INTEGER, new String[] {key},
                Integer.toString(expectedTurn), Integer.toString(next.turnIndex()), payload, Long.toString(ttlMillis)));
        return Long.valueOf(1L).equals(applied);
    }

    void delete(long owner, String id) {
        redis(commands -> commands.del(codec.key(owner, id)));
    }

    private <T> T redis(Function<RedisCommands<String, String>, T> operation) {
        try {
            return operation.apply(connection().sync());
        } catch (RedisException error) {
            throw new ConversationPersistenceUnavailableException();
        }
    }

    private synchronized StatefulRedisConnection<String, String> connection() {
        if (connection == null || !connection.isOpen()) {
            if (retryConnectAtNanos != 0 && System.nanoTime() - retryConnectAtNanos < 0) {
                throw new ConversationPersistenceUnavailableException();
            }
            try {
                connection = client.connect();
                retryConnectAtNanos = 0;
            } catch (RedisException error) {
                retryConnectAtNanos = System.nanoTime() + Duration.ofSeconds(1).toNanos();
                throw error;
            }
        }
        return connection;
    }

    @PreDestroy
    @Override
    public synchronized void close() {
        if (connection != null) {
            connection.close();
            connection = null;
        }
        if (client != null) {
            client.shutdown();
        }
    }

    private static RedisURI endpoint(String raw, Duration timeout) {
        try {
            String value = raw.strip();
            URI parsed = URI.create(value);
            if (!("redis".equals(parsed.getScheme()) || "rediss".equals(parsed.getScheme())) || parsed.getHost() == null) {
                throw new IllegalArgumentException();
            }
            RedisURI endpoint = RedisURI.create(value);
            endpoint.setTimeout(timeout);
            return endpoint;
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("RAG conversation persistence requires a valid Redis endpoint.");
        }
    }
}
