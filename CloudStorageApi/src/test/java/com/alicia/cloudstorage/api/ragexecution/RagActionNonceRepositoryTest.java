package com.alicia.cloudstorage.api.ragexecution;

import com.alicia.cloudstorage.api.ragexecution.persistence.RagActionNonceEntity;
import com.alicia.cloudstorage.api.ragexecution.persistence.RagActionNonceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class RagActionNonceRepositoryTest {

    @Autowired
    private RagActionNonceRepository repository;

    @Test
    void bulkCleanupDeletesOnlyExpiredNonces() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 14, 5, 43);
        repository.save(RagActionNonceEntity.create(
                "rag-execution", "expired-nonce-0001", now.minusSeconds(1), now.minusMinutes(3)
        ));
        repository.save(RagActionNonceEntity.create(
                "rag-execution", "expired-nonce-0002", now.minusMinutes(1), now.minusMinutes(4)
        ));
        repository.saveAndFlush(RagActionNonceEntity.create(
                "rag-execution", "active-nonce-000001", now.plusMinutes(3), now
        ));

        int deleted = repository.deleteExpiredBefore(now);
        repository.flush();

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findAll())
                .singleElement()
                .isNotNull();
        assertThat(repository.existsById("rag-execution:active-nonce-000001")).isTrue();
    }
}
