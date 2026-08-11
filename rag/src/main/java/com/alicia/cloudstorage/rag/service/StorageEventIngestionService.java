package com.alicia.cloudstorage.rag.service;

import com.alicia.cloudstorage.rag.dto.StorageNodeChangeRequest;
import com.alicia.cloudstorage.rag.dto.StorageNodeChangeType;
import com.alicia.cloudstorage.rag.dto.StorageNodeReferenceRequest;
import org.springframework.stereotype.Service;

@Service
public class StorageEventIngestionService {

    private final IndexingTaskPort indexingTaskPort;

    public StorageEventIngestionService(IndexingTaskPort indexingTaskPort) {
        this.indexingTaskPort = indexingTaskPort;
    }

    public void ingest(StorageNodeChangeRequest request) {
        for (StorageNodeReferenceRequest reference : request.nodeReferences()) {
            IndexingTask task = new IndexingTask(
                    request.eventId(),
                    reference.ownerId(),
                    reference.nodeId(),
                    request.includeDescendants(),
                    request.occurredAt()
            );

            if (request.changeType() == StorageNodeChangeType.UPSERT) {
                indexingTaskPort.enqueueUpsert(task);
            } else {
                indexingTaskPort.enqueueRemove(task);
            }
        }
    }
}
