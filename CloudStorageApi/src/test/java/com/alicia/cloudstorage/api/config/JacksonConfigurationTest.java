package com.alicia.cloudstorage.api.config;

import com.alicia.cloudstorage.api.storage.StorageNodeChangeType;
import com.alicia.cloudstorage.api.storage.StorageNodeChangeWebhookForwarder;
import com.alicia.cloudstorage.api.storage.StorageNodeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigurationTest {

    @Test
    void objectMapperSerializesStorageEventInstants() throws Exception {
        ObjectMapper objectMapper = new JacksonConfiguration().objectMapper();
        StorageNodeChangeWebhookForwarder.StorageNodeChangeWebhookPayload payload =
                new StorageNodeChangeWebhookForwarder.StorageNodeChangeWebhookPayload(
                        "event-1",
                        StorageNodeChangeType.UPSERT,
                        List.of(new StorageNodeReference(12L, 34L)),
                        false,
                        Instant.parse("2026-08-11T12:00:00Z")
                );

        String json = objectMapper.writeValueAsString(payload);

        assertThat(json).contains("\"occurredAt\":\"2026-08-11T12:00:00Z\"");
    }
}
