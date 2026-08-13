package com.alicia.cloudstorage.api.config;

import com.alicia.cloudstorage.api.storage.StorageNodeChangeType;
import com.alicia.cloudstorage.api.storage.StorageNodeChangeWebhookForwarder;
import com.alicia.cloudstorage.api.storage.StorageNodeReference;
import com.alicia.cloudstorage.api.dto.StorageNodeSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    @Test
    void storageNodeTimestampsIncludeTheServerOffset() throws Exception {
        ObjectMapper objectMapper = new JacksonConfiguration().objectMapper();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 13, 15, 31, 19);
        StorageNodeSummaryResponse response = new StorageNodeSummaryResponse(
                1L,
                null,
                "sample.png",
                "FILE",
                1024L,
                "png",
                "image/png",
                updatedAt,
                null
        );

        String json = objectMapper.writeValueAsString(response);
        String expected = updatedAt.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        assertThat(json).contains("\"updatedAt\":\"" + expected + "\"");
        assertThat(json).contains("\"deletedAt\":null");
    }
}
