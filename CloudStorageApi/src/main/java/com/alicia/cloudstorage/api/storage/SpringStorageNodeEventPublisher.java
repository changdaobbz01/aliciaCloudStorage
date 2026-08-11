package com.alicia.cloudstorage.api.storage;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class SpringStorageNodeEventPublisher implements StorageNodeEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringStorageNodeEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(
            StorageNodeChangeType changeType,
            Collection<StorageNodeReference> references,
            boolean includeDescendants
    ) {
        if (references == null || references.isEmpty()) {
            return;
        }

        StorageNodeChangeEvent event = new StorageNodeChangeEvent(
                changeType,
                List.copyOf(references),
                includeDescendants
        );
        if (!event.isEmpty()) {
            applicationEventPublisher.publishEvent(event);
        }
    }
}
