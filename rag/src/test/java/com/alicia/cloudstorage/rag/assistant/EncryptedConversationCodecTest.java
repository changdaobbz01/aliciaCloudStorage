package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedConversationCodecTest {

    static final String TEST_KEY = "ZmFrZS1yYWctY29udmVyc2F0aW9uLWtleS0zMi1ieXQ=";

    private final EncryptedConversationCodec codec = new EncryptedConversationCodec(TEST_KEY, new ObjectMapper().findAndRegisterModules());

    @Test
    void sealsReadableStateAndAuthenticatesTheRedisKey() {
        String id = UUID.randomUUID().toString();
        String key = codec.key(23, id);
        var state = new AssistantConversationState(id, 3, "folder_create", Map.of("name", "private folder"),
                List.of(), null, null, null, null, null,
                "previous-token-fingerprint", Instant.now().plusSeconds(30),
                List.of(new AssistantConversationTurn("private utterance", "private reply")));

        String envelope = codec.encode(key, state);
        assertThat(key).doesNotContain(id).isNotEqualTo(codec.key(24, id));
        assertThat(envelope).startsWith("v1.").doesNotContain("private folder", "private utterance", id);
        assertThat(codec.encode(key, state)).isNotEqualTo(envelope);

        AssistantConversationState restored = codec.decode(key, envelope);
        assertThat(restored).isEqualTo(state.withoutAuthorizationFingerprint());
        assertThat(restored.authorizationFingerprint()).isEmpty();
        assertThatThrownBy(() -> codec.decode(codec.key(24, id), envelope))
                .isInstanceOf(ConversationPersistenceUnavailableException.class);
        String tampered = envelope.substring(0, 12) + (envelope.charAt(12) == 'A' ? 'B' : 'A') + envelope.substring(13);
        assertThatThrownBy(() -> codec.decode(key, tampered)).isInstanceOf(ConversationPersistenceUnavailableException.class);
    }

    @Test
    void boundsRecentHistoryAndPreservesUnicodeCharacters() {
        List<AssistantConversationTurn> turns = List.of();
        for (int turn = 0; turn < 10; turn++) {
            turns = AssistantConversationTurn.append(turns, "turn " + turn, "🚀".repeat(513));
        }
        assertThat(turns).hasSize(8);
        assertThat(turns.getFirst().userText()).isEqualTo("turn 2");
        assertThat(turns.getLast().userText()).isEqualTo("turn 9");
        assertThat(turns.getLast().assistantText()).isEqualTo("🚀".repeat(512));
    }
}
