package com.alicia.cloudstorage.ragexecution.integration;

import com.alicia.cloudstorage.ragexecution.RagExecutionApplication;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionRisk;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionStatus;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaEntity;
import com.alicia.cloudstorage.ragexecution.infrastructure.persistence.ExecutionJpaRepository;
import com.alicia.cloudstorage.ragexecution.port.ExecutionLeaseStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceRestartTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void taskRemainsAvailableAfterTheApplicationContextRestarts() {
        String databasePath = temporaryDirectory.resolve("rag-execution-restart")
                .toAbsolutePath()
                .toString()
                .replace('\\', '/');
        String url = "jdbc:h2:file:" + databasePath
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE";
        UUID executionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-18T04:00:00Z");

        try (ConfigurableApplicationContext first = start(url)) {
            ExecutionJpaRepository repository = first.getBean(ExecutionJpaRepository.class);
            ExecutionJpaEntity execution = ExecutionJpaEntity.createPending(
                    executionId,
                    42L,
                    "conversation-restart",
                    "response-restart",
                    "plan-restart",
                    "action_plan_v2",
                    "b".repeat(64),
                    "Restart persistence probe",
                    ExecutionRisk.LOW,
                    "restart-idempotency",
                    now.plusSeconds(600),
                    now
            );
            execution.transitionTo(ExecutionStatus.QUEUED, now.plusSeconds(1));
            repository.saveAndFlush(execution);
        }

        try (ConfigurableApplicationContext second = start(url)) {
            ExecutionJpaRepository repository = second.getBean(ExecutionJpaRepository.class);
            assertThat(repository.findById(executionId.toString()))
                    .isPresent()
                    .get()
                    .satisfies(execution -> {
                        assertThat(execution.getId()).isEqualTo(executionId);
                        assertThat(execution.getOwnerUserId()).isEqualTo(42L);
                        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
                        assertThat(execution.getVersion()).isZero();
                    });

            ExecutionLeaseStore leaseStore = second.getBean(ExecutionLeaseStore.class);
            assertThat(leaseStore.claimNext("restart-worker", now.plusSeconds(2), Duration.ofSeconds(30)))
                    .isPresent()
                    .get()
                    .satisfies(lease -> {
                        assertThat(lease.executionId()).isEqualTo(executionId);
                        assertThat(lease.leaseOwner()).isEqualTo("restart-worker");
                        assertThat(lease.version()).isEqualTo(1L);
                    });
        }
    }

    private ConfigurableApplicationContext start(String url) {
        return new SpringApplicationBuilder(RagExecutionApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.main.banner-mode=off",
                        "--logging.level.root=WARN"
                );
    }
}
