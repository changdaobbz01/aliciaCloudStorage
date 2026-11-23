package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alicia.cloudstorage.rag.assistant.EncryptedConversationCodecTest.TEST_KEY;
import static com.alicia.cloudstorage.rag.assistant.RedisConversationStateRepository.Mode.REDIS;
import static com.alicia.cloudstorage.rag.assistant.RedisConversationStateRepository.Mode.SHADOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "ALICIA_TEST_RAG_REDIS_URL", matches = "redis://127\\.0\\.0\\.1:[0-9]+(/[0-9]+)?")
class RedisConversationStateRepositoryTest {

    private static final String URL = System.getenv("ALICIA_TEST_RAG_REDIS_URL");
    private static final long OWNER = 713L;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final RagConfigLoader config = new RagConfigLoader(json);

    @Test
    void restoresWithARefreshedTokenThroughAnIndependentStoreAndFencesConcurrentWrites() throws Exception {
        try (var first = redis(REDIS); var second = redis(REDIS);
             var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var firstStore = store(first);
            var firstTurn = firstStore.resolve("", "Bearer original", OWNER);
            var saved = firstStore.save(firstTurn, draft(), "在当前目录建一个文件夹", OWNER);

            var secondStore = store(second);
            var restored = secondStore.resolve(saved.conversationId(), "Bearer refreshed", OWNER);
            assertThat(restored.conversationId()).isEqualTo(saved.conversationId());
            assertThat(restored.pendingActionPlan()).isEqualTo(saved.pendingActionPlan());
            assertThat(restored.pendingSlots()).isEqualTo(saved.pendingSlots());
            assertThat(restored.semanticFrame()).isEqualTo(saved.semanticFrame());
            assertThat(restored.recentTurns()).containsExactly(new AssistantConversationTurn(
                    "在当前目录建一个文件夹", saved.recentTurns().getFirst().assistantText()));
            assertThat(secondStore.resolve(saved.conversationId(), "Bearer original", OWNER + 1).turnIndex()).isZero();

            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var committed = new AtomicInteger();
            var conflicts = new AtomicInteger();
            var requests = new ArrayList<java.util.concurrent.Future<?>>();
            for (var instance : List.of(firstStore, secondStore)) {
                requests.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent test did not start.");
                    }
                    try {
                        instance.save(restored, draft(), "同一轮的修改", OWNER);
                        committed.incrementAndGet();
                    } catch (AssistantConversationStore.ConversationContinuityException rejected) {
                        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        conflicts.incrementAndGet();
                    }
                    return null;
                }));
            }
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var request : requests) {
                request.get(2, TimeUnit.SECONDS);
            }
            assertThat(committed).hasValue(1);
            assertThat(conflicts).hasValue(1);

            var beforeDelete = firstStore.resolve(saved.conversationId(), "Bearer refreshed", OWNER);
            secondStore.complete(saved.conversationId(), "Bearer refreshed", OWNER);
            assertThat(first.find(OWNER, saved.conversationId())).isEmpty();
            assertThatThrownBy(() -> firstStore.save(beforeDelete, draft(), "旧请求", OWNER))
                    .isInstanceOfSatisfying(AssistantConversationStore.ConversationContinuityException.class,
                            rejected -> assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }
    }

    @Test
    void shadowMirrorsEncryptedStateThatRedisModeCanRead() {
        try (var mirror = redis(SHADOW); var active = redis(REDIS); var redisClient = RedisClient.create(URL);
             var raw = redisClient.connect()) {
            var shadowStore = store(mirror);
            var state = shadowStore.save(shadowStore.resolve("", "Bearer initial", OWNER), draft(), "private context", OWNER);
            assertThat(mirror.matches(OWNER, state)).isTrue();
            var activeStore = store(active);

            var adopted = activeStore.resolve(state.conversationId(), "Bearer renewed", OWNER);
            assertThat(adopted.turnIndex()).isEqualTo(1);
            assertThat(adopted.authorizationFingerprint()).isEmpty();
            assertThat(adopted.recentTurns().getFirst().userText()).isEqualTo("private context");

            var codec = new EncryptedConversationCodec(TEST_KEY, json);
            String key = codec.key(OWNER, state.conversationId());
            assertThat(raw.sync().hget(key, "payload")).startsWith("v1.").doesNotContain("private context", "Bearer");
            assertThat(raw.sync().pttl(key)).isBetween(1L, 30 * 60_000L);
            shadowStore.reset(state.conversationId(), "Bearer initial", OWNER);
            assertThat(active.find(OWNER, state.conversationId())).isEmpty();
        }
    }

    @Test
    void storageFailureStartsFreshButRefusesToPublishAPlanBasedOnPreviouslyLoadedContext() {
        try (var healthy = redis(REDIS);
             var broken = new RedisConversationStateRepository(REDIS, unavailableUrl(), TEST_KEY, 150, json)) {
            var healthyStore = store(healthy);
            var previous = healthyStore.save(healthyStore.resolve("", "Bearer test", OWNER), draft(), "已知对话", OWNER);
            var degradedStore = store(broken);

            var fresh = degradedStore.resolve(previous.conversationId(), "Bearer test", OWNER);
            assertThat(fresh.conversationId()).isNotEqualTo(previous.conversationId());
            assertThat(fresh.turnIndex()).isZero();
            assertThat(fresh.pendingActionPlan()).isNull();
            assertThat(fresh.recentTurns()).isEmpty();
            assertThat(degradedStore.save(fresh, draft(), "全新输入", OWNER).turnIndex()).isEqualTo(1);
            assertThatThrownBy(() -> degradedStore.save(previous, draft(), "可能复用旧计划", OWNER))
                    .isInstanceOfSatisfying(AssistantConversationStore.ConversationContinuityException.class,
                            rejected -> assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
            assertThatThrownBy(() -> degradedStore.complete(previous.conversationId(), "Bearer test", OWNER))
                    .isInstanceOf(AssistantConversationStore.ConversationContinuityException.class);
            healthy.delete(OWNER, previous.conversationId());
        }
    }

    @Test
    void redisExpiresStateServerSide() throws Exception {
        try (var redis = redis(REDIS)) {
            var id = java.util.UUID.randomUUID().toString();
            var now = Instant.now();
            var expiring = new AssistantConversationState(id, 1, "folder_create", Map.of(), List.of(),
                    null, null, null, null, null, "", now.plusMillis(150));
            assertThat(redis.save(OWNER, 0, expiring, now)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (redis.find(OWNER, id).isPresent() && System.nanoTime() < deadline) {
                Thread.sleep(30);
            }
            assertThat(redis.find(OWNER, id)).isEmpty();
        }
    }

    private RedisConversationStateRepository redis(RedisConversationStateRepository.Mode mode) {
        return new RedisConversationStateRepository(mode, URL, TEST_KEY, 1500, json);
    }

    private static AssistantConversationStore store(RedisConversationStateRepository repository) {
        return new AssistantConversationStore(new InMemoryAssistantConversationStateRepository(), 30, 500, repository);
    }

    private IntentRecognitionResponse draft() {
        var router = new IntentRouter(config, new EntityExtractor(config));
        return new IntentRecognitionService(ignored -> Optional.empty(), router, config)
                .recognizeLocal("请在当前目录建文件夹", "local fixture")
                .withActionPlan(new ActionPlan("action_plan_v2", "ap-fixture", "review_required", "atomic",
                        "folder.create", "medium", "confirm", "zh-CN", Map.of(),
                        List.of(new ActionPlanStep("create", "folder.create", "pending", Map.of("name", "fixture"),
                                List.of(), List.of(), "")), List.of(), "新建文件夹", List.of()));
    }

    private static String unavailableUrl() {
        int port = URI.create(URL).getPort() == 1 ? 2 : 1;
        return "redis://127.0.0.1:" + port;
    }
}
