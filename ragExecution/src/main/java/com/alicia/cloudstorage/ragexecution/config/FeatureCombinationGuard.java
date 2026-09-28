package com.alicia.cloudstorage.ragexecution.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import com.alicia.cloudstorage.ragexecution.domain.ExecutionActionType;

@Component
public class FeatureCombinationGuard {

    private static final Set<ExecutionActionType> SERVER_ACTIONS = EnumSet.of(
            ExecutionActionType.NODE_RENAME,
            ExecutionActionType.NODE_TRASH,
            ExecutionActionType.NODE_MOVE,
            ExecutionActionType.FOLDER_CREATE,
            ExecutionActionType.SHARE_CREATE,
            ExecutionActionType.NODE_BATCH_TRASH,
            ExecutionActionType.NODE_BATCH_MOVE,
            ExecutionActionType.NODE_BATCH_RENAME,
            ExecutionActionType.UPLOAD_FILES
    );

    private final RagExecutionFeatureProperties features;
    private final RagExecutionSecurityProperties security;
    private final RagExecutionCloudProperties cloud;

    @Autowired
    public FeatureCombinationGuard(
            RagExecutionFeatureProperties features,
            RagExecutionSecurityProperties security,
            RagExecutionCloudProperties cloud
    ) {
        this.features = features;
        this.security = security;
        this.cloud = cloud;
    }

    FeatureCombinationGuard(RagExecutionFeatureProperties features) {
        this(features, new RagExecutionSecurityProperties(
                "",
                Duration.ofSeconds(60),
                Duration.ofMinutes(3)
        ), new RagExecutionCloudProperties(
                "http://localhost:8080",
                "",
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)
        ));
    }

    @PostConstruct
    void validate() {
        if (!features.enabled()
                && (features.registrationEnabled()
                || features.publicConfirmEnabled()
                || features.workerEnabled()
                || features.cloudDispatchEnabled())) {
            throw new IllegalStateException("RAG execution sub-features require the master feature to be enabled.");
        }
        if (features.publicConfirmEnabled() && !features.adminOnly()) {
            throw new IllegalStateException("Phase 4 public confirmation must remain restricted to RAG_ADMIN.");
        }
        if (features.publicConfirmEnabled() && features.allowedActions().isEmpty()) {
            throw new IllegalStateException("Public confirmation requires an explicit action allowlist.");
        }
        if (!SERVER_ACTIONS.containsAll(features.allowedActions())) {
            throw new IllegalStateException("The action allowlist contains actions outside the server execution contract.");
        }
        if (features.cloudDispatchEnabled() && !features.workerEnabled()) {
            throw new IllegalStateException("Cloud dispatch requires the execution worker to be enabled.");
        }
        if (features.registrationEnabled() && security.serviceSecret().length() < 32) {
            throw new IllegalStateException("Plan registration requires a dedicated service secret of at least 32 characters.");
        }
        if (features.cloudDispatchEnabled() && cloud.serviceSecret().length() < 32) {
            throw new IllegalStateException("Cloud dispatch requires a dedicated service secret of at least 32 characters.");
        }
        if (features.registrationEnabled()
                && features.cloudDispatchEnabled()
                && security.serviceSecret().equals(cloud.serviceSecret())) {
            throw new IllegalStateException(
                    "Plan registration and Cloud dispatch must use different service secrets."
            );
        }
    }
}
