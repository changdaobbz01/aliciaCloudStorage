package com.alicia.cloudstorage.ragexecution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("alicia.rag-execution.limits")
public record RagExecutionLimitsProperties(
        int maxSteps,
        int maxBatchNodes,
        int maxPlanBytes,
        Duration confirmationTtl,
        Duration maxQueueWait,
        Duration clientInputTtl,
        Duration leaseDuration,
        int maxAttempts
) {
    private static final int HARD_MAX_STEPS = 50;
    private static final int HARD_MAX_BATCH_NODES = 500;
    private static final int HARD_MAX_PLAN_BYTES = 262_144;

    public RagExecutionLimitsProperties {
        requireRange(maxSteps, 1, HARD_MAX_STEPS, "maxSteps");
        requireRange(maxBatchNodes, 1, HARD_MAX_BATCH_NODES, "maxBatchNodes");
        requireRange(maxPlanBytes, 1_024, HARD_MAX_PLAN_BYTES, "maxPlanBytes");
        requirePositive(confirmationTtl, "confirmationTtl");
        requirePositive(maxQueueWait, "maxQueueWait");
        requirePositive(clientInputTtl, "clientInputTtl");
        requirePositive(leaseDuration, "leaseDuration");
        requireRange(maxAttempts, 1, 10, "maxAttempts");
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum + ".");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
    }
}
