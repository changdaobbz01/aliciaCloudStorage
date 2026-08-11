package com.alicia.cloudstorage.rag.service;

public interface IndexingTaskPort {

    void enqueueUpsert(IndexingTask task);

    void enqueueRemove(IndexingTask task);
}
