package com.alicia.cloudstorage.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NoopIndexingTaskPort implements IndexingTaskPort {

    private static final Logger log = LoggerFactory.getLogger(NoopIndexingTaskPort.class);

    @Override
    public void enqueueUpsert(IndexingTask task) {
        log.info("Accepted storage upsert event for future RAG indexing: eventId={}, nodeId={}", task.eventId(), task.nodeId());
    }

    @Override
    public void enqueueRemove(IndexingTask task) {
        log.info("Accepted storage remove event for future RAG indexing: eventId={}, nodeId={}", task.eventId(), task.nodeId());
    }
}
