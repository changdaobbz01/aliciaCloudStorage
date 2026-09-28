package com.alicia.cloudstorage.ragexecution.config;

import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties("alicia.rag-execution.features")
public record RagExecutionFeatureProperties(
        boolean enabled,
        boolean registrationEnabled,
        boolean publicConfirmEnabled,
        boolean workerEnabled,
        boolean cloudDispatchEnabled,
        boolean adminOnly,
        Set<ExecutionActionType> allowedActions
) {

    public RagExecutionFeatureProperties {
        allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
    }

}
