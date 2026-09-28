package com.alicia.cloudstorage.ragexecution.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagExecutionConfigurationTest {

    @Test
    void defaultDisabledFeatureCombinationIsValid() {
        FeatureCombinationGuard guard = new FeatureCombinationGuard(
                features(false, false, false, false)
        );
        assertThatCode(guard::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsAccidentalPartialEnablement() {
        FeatureCombinationGuard registrationWithoutMaster = new FeatureCombinationGuard(
                features(false, true, false, false)
        );
        FeatureCombinationGuard dispatchWithoutWorker = new FeatureCombinationGuard(
                features(true, false, false, true)
        );

        assertThatThrownBy(registrationWithoutMaster::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("master feature");
        assertThatThrownBy(dispatchWithoutWorker::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires the execution worker");
    }

    @Test
    void rejectsSharedRegistrationAndCloudDispatchSecrets() {
        String sharedSecret = "shared-service-secret-that-is-long-enough";
        FeatureCombinationGuard guard = new FeatureCombinationGuard(
                features(true, true, true, true),
                new RagExecutionSecurityProperties(
                        sharedSecret,
                        Duration.ofSeconds(60),
                        Duration.ofMinutes(3)
                ),
                new RagExecutionCloudProperties(
                        "http://api:8080",
                        sharedSecret,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5)
                )
        );

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different service secrets");
    }

    @Test
    void limitsHaveHardServerSideCaps() {
        assertThatCode(() -> validLimits(10, 100, 65_536)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validLimits(51, 100, 65_536))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxSteps");
        assertThatThrownBy(() -> validLimits(10, 501, 65_536))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBatchNodes");
        assertThatThrownBy(() -> validLimits(10, 100, 262_145))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPlanBytes");
    }

    private RagExecutionLimitsProperties validLimits(int steps, int nodes, int bytes) {
        return new RagExecutionLimitsProperties(
                steps,
                nodes,
                bytes,
                Duration.ofMinutes(10),
                Duration.ofMinutes(2),
                Duration.ofMinutes(15),
                Duration.ofSeconds(30),
                3
        );
    }

    private RagExecutionFeatureProperties features(
            boolean enabled,
            boolean registrationEnabled,
            boolean workerEnabled,
            boolean cloudDispatchEnabled
    ) {
        return new RagExecutionFeatureProperties(
                enabled,
                registrationEnabled,
                false,
                workerEnabled,
                cloudDispatchEnabled,
                true,
                Set.of()
        );
    }
}
