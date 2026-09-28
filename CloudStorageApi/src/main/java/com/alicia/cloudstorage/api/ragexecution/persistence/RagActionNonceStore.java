package com.alicia.cloudstorage.api.ragexecution.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class RagActionNonceStore {

    private final RagActionNonceRepository repository;

    public RagActionNonceStore(RagActionNonceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void consume(String caller, String nonce, Instant expiresAt, Instant now) {
        LocalDateTime created = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        repository.deleteByExpiresAtBefore(created);
        repository.saveAndFlush(RagActionNonceEntity.create(
                caller,
                nonce,
                LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC),
                created
        ));
    }
}
