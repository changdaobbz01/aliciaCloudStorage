package com.alicia.cloudstorage.rag.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

final class EncryptedConversationCodec {

    private static final String PREFIX = "alicia:rag:conversation:v1:";
    private static final String VERSION = "v1.";
    private static final int NONCE_BYTES = 12;
    private static final int MAX_PLAINTEXT = 512 * 1024;
    private static final int MAX_ENVELOPE = (MAX_PLAINTEXT + 64) * 4 / 3;
    private final SecretKeySpec routeKey;
    private final SecretKeySpec encryptionKey;
    private final SecureRandom random = new SecureRandom();
    private final ObjectMapper json;

    EncryptedConversationCodec(String base64Secret, ObjectMapper json) {
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(base64Secret.strip());
        } catch (IllegalArgumentException error) {
            throw invalidKey();
        }
        if (secret.length != 32) {
            throw invalidKey();
        }
        var root = new SecretKeySpec(secret, "HmacSHA256");
        this.routeKey = new SecretKeySpec(hmac(root, "alicia-rag-session-routing-v1"), "HmacSHA256");
        this.encryptionKey = new SecretKeySpec(hmac(root, "alicia-rag-session-encryption-v1"), "AES");
        this.json = json;
    }

    String key(long userId, String conversationId) {
        return PREFIX + HexFormat.of().formatHex(hmac(routeKey, userId + ":" + conversationId));
    }

    String encode(String key, AssistantConversationState value) {
        try {
            byte[] plaintext = json.writeValueAsBytes(value.withoutAuthorizationFingerprint());
            if (plaintext.length > MAX_PLAINTEXT) {
                throw new ConversationPersistenceUnavailableException();
            }
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, key, nonce).doFinal(plaintext);
            return VERSION + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException | IOException error) {
            throw new ConversationPersistenceUnavailableException();
        }
    }

    AssistantConversationState decode(String key, String encoded) {
        if (encoded == null || !encoded.startsWith(VERSION) || encoded.length() > MAX_ENVELOPE) {
            throw new ConversationPersistenceUnavailableException();
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded.substring(VERSION.length()));
            if (bytes.length < NONCE_BYTES + 16) {
                throw new ConversationPersistenceUnavailableException();
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            byte[] plaintext = cipher(Cipher.DECRYPT_MODE, key, nonce).doFinal(encrypted);
            return json.readValue(plaintext, AssistantConversationState.class);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException error) {
            throw new ConversationPersistenceUnavailableException();
        }
    }

    boolean sameState(AssistantConversationState left, AssistantConversationState right) {
        try {
            return json.readTree(json.writeValueAsBytes(left.withoutAuthorizationFingerprint()))
                    .equals(json.readTree(json.writeValueAsBytes(right.withoutAuthorizationFingerprint())));
        } catch (IOException error) {
            throw new ConversationPersistenceUnavailableException();
        }
    }

    private Cipher cipher(int mode, String key, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, encryptionKey, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(key.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private static byte[] hmac(SecretKeySpec secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secret);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException error) {
            throw new ConversationPersistenceUnavailableException();
        }
    }

    private static IllegalArgumentException invalidKey() {
        return new IllegalArgumentException("RAG conversation persistence requires a base64-encoded 32-byte key.");
    }
}
