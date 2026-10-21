package com.alicia.cloudstorage.ragexecution.api;

import com.alicia.cloudstorage.ragexecution.config.RagExecutionFeatureProperties;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;
import com.alicia.cloudstorage.ragexecution.port.DependencyHealth;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RagExecutionHealthControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-18T04:00:00Z");

    @Test
    void livenessShowsEveryExecutionFeatureDisabledByDefault() {
        RagExecutionHealthController controller = controller(new DependencyHealth(true, "reachable"));

        RagExecutionHealthController.LivenessResponse response = controller.health();

        assertThat(response.status()).isEqualTo("ok");
        assertThat(response.service()).isEqualTo("rag-execution-service");
        assertThat(response.timestamp()).isEqualTo(NOW);
        assertThat(response.features().enabled()).isFalse();
        assertThat(response.features().registrationEnabled()).isFalse();
        assertThat(response.features().workerEnabled()).isFalse();
        assertThat(response.features().cloudDispatchEnabled()).isFalse();
        assertThat(response.features().allowedActions()).isEmpty();
    }

    @Test
    void dependencyHealthDegradesWithoutLeakingDatabaseExceptionDetails() {
        RagExecutionHealthController controller = controller(new DependencyHealth(false, "probe-failed"));

        var response = controller.dependencies();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("degraded");
        assertThat(response.getBody().dependencies().database().detail()).isEqualTo("probe-failed");
        assertThat(response.getBody().dependencies().identity().status()).isEqualTo("inactive");
        assertThat(response.getBody().dependencies().cloudStorage().status()).isEqualTo("inactive");
    }

    @Test
    void livenessReportsTheEffectiveActionAllowlistInStableOrder() {
        RagExecutionHealthController controller = new RagExecutionHealthController(
                new RagExecutionFeatureProperties(
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        Set.of(ExecutionActionType.NODE_RENAME, ExecutionActionType.FOLDER_CREATE)
                ),
                () -> new DependencyHealth(true, "reachable"),
                () -> NOW
        );

        assertThat(controller.health().features().allowedActions())
                .containsExactly("FOLDER_CREATE", "NODE_RENAME");
    }

    private RagExecutionHealthController controller(DependencyHealth health) {
        return new RagExecutionHealthController(
                new RagExecutionFeatureProperties(false, false, false, false, false, true, Set.of()),
                () -> health,
                () -> NOW
        );
    }
}
