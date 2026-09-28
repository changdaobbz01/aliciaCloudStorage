package com.alicia.cloudstorage.ragexecution.infrastructure.persistence;

import com.alicia.cloudstorage.ragexecution.port.ServiceNonceStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class ServiceNoncePersistenceAdapter implements ServiceNonceStore {

    private final ServiceNonceJpaRepository repository;

    public ServiceNoncePersistenceAdapter(ServiceNonceJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consume(String callerService, String nonce, Instant expiresAt, Instant now) {
        repository.deleteExpiredBefore(now);
        String id = callerService + ":" + nonce;
        if (repository.existsById(id)) {
            throw new IllegalStateException("Service nonce has already been used.");
        }
        repository.saveAndFlush(ServiceNonceJpaEntity.create(callerService, nonce, expiresAt, now));
    }
}
