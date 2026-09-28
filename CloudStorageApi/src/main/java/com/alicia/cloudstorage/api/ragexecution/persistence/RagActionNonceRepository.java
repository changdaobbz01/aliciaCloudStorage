package com.alicia.cloudstorage.api.ragexecution.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface RagActionNonceRepository extends JpaRepository<RagActionNonceEntity, String> {

    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
