package com.alicia.cloudstorage.api.ragexecution.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface RagActionNonceRepository extends JpaRepository<RagActionNonceEntity, String> {

    @Modifying
    @Query("delete from RagActionNonceEntity nonce where nonce.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
