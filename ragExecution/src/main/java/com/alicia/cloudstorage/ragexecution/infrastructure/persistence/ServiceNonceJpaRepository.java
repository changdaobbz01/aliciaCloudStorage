package com.alicia.cloudstorage.ragexecution.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ServiceNonceJpaRepository extends JpaRepository<ServiceNonceJpaEntity, String> {

    @Modifying
    @Query("delete from ServiceNonceJpaEntity nonce where nonce.expiresAt < :now")
    int deleteExpiredBefore(@Param("now") Instant now);
}
