package com.alicia.cloudstorage.rag.service;

import com.alicia.cloudstorage.rag.dto.StorageNodeChangeRequest;
import com.alicia.cloudstorage.rag.dto.StorageNodeChangeType;
import com.alicia.cloudstorage.rag.dto.StorageNodeReferenceRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StorageEventIngestionServiceTest {

    @Mock
    private IndexingTaskPort indexingTaskPort;

    @Test
    void ingestRoutesUpsertEventsToUpsertTaskPort() {
        StorageEventIngestionService service = new StorageEventIngestionService(indexingTaskPort);
        Instant occurredAt = Instant.parse("2026-08-10T08:00:00Z");

        service.ingest(new StorageNodeChangeRequest(
                "event-1",
                StorageNodeChangeType.UPSERT,
                List.of(new StorageNodeReferenceRequest(7L, 21L)),
                true,
                occurredAt
        ));

        ArgumentCaptor<IndexingTask> taskCaptor = ArgumentCaptor.forClass(IndexingTask.class);
        verify(indexingTaskPort).enqueueUpsert(taskCaptor.capture());
        verify(indexingTaskPort, never()).enqueueRemove(org.mockito.ArgumentMatchers.any());

        assertThat(taskCaptor.getValue().eventId()).isEqualTo("event-1");
        assertThat(taskCaptor.getValue().ownerId()).isEqualTo(7L);
        assertThat(taskCaptor.getValue().nodeId()).isEqualTo(21L);
        assertThat(taskCaptor.getValue().includeDescendants()).isTrue();
        assertThat(taskCaptor.getValue().occurredAt()).isEqualTo(occurredAt);
    }
}
