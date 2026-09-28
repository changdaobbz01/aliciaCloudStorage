package com.alicia.cloudstorage.ragexecution.api;

import com.alicia.cloudstorage.ragexecution.config.RagExecutionFeatureProperties;
import com.alicia.cloudstorage.ragexecution.port.DatabaseHealthProbe;
import com.alicia.cloudstorage.ragexecution.port.CloudHealthProbe;
import com.alicia.cloudstorage.ragexecution.port.DependencyHealth;
import com.alicia.cloudstorage.ragexecution.port.ExecutionClock;
import com.alicia.cloudstorage.ragexecution.port.IdentityHealthProbe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class RagExecutionHealthController {

    private static final String SERVICE_NAME = "rag-execution-service";

    private final RagExecutionFeatureProperties features;
    private final DatabaseHealthProbe databaseHealthProbe;
    private final ExecutionClock clock;
    private final IdentityHealthProbe identityHealthProbe;
    private final CloudHealthProbe cloudHealthProbe;

    @Autowired
    public RagExecutionHealthController(
            RagExecutionFeatureProperties features,
            DatabaseHealthProbe databaseHealthProbe,
            ExecutionClock clock,
            IdentityHealthProbe identityHealthProbe,
            CloudHealthProbe cloudHealthProbe
    ) {
        this.features = features;
        this.databaseHealthProbe = databaseHealthProbe;
        this.clock = clock;
        this.identityHealthProbe = identityHealthProbe;
        this.cloudHealthProbe = cloudHealthProbe;
    }

    RagExecutionHealthController(
            RagExecutionFeatureProperties features,
            DatabaseHealthProbe databaseHealthProbe,
            ExecutionClock clock
    ) {
        this(
                features,
                databaseHealthProbe,
                clock,
                () -> new DependencyHealth(false, "inactive"),
                () -> new DependencyHealth(false, "inactive")
        );
    }

    @GetMapping("/api/health")
    public LivenessResponse health() {
        return new LivenessResponse("ok", SERVICE_NAME, FeatureState.from(features), clock.now());
    }

    @GetMapping("/api/health/dependencies")
    public ResponseEntity<DependencyHealthResponse> dependencies() {
        DependencyHealth database = databaseHealthProbe.check();
        boolean identityRequired = features.enabled() || features.registrationEnabled();
        DependencyHealth identity = identityRequired
                ? identityHealthProbe.check()
                : new DependencyHealth(false, "execution-disabled");
        DependencyHealth cloud = features.cloudDispatchEnabled()
                ? cloudHealthProbe.check()
                : new DependencyHealth(false, "cloud-dispatch-disabled");
        boolean healthy = database.available()
                && (!identityRequired || identity.available())
                && (!features.cloudDispatchEnabled() || cloud.available());
        DependencyHealthResponse response = new DependencyHealthResponse(
                healthy ? "ok" : "degraded",
                SERVICE_NAME,
                new DependencySet(
                        DependencyState.from(database),
                        identityRequired
                                ? DependencyState.from(identity)
                                : DependencyState.inactive("execution-disabled"),
                        features.cloudDispatchEnabled()
                                ? DependencyState.from(cloud)
                                : DependencyState.inactive("cloud-dispatch-disabled")
                ),
                clock.now()
        );
        return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }

    public record LivenessResponse(
            String status,
            String service,
            FeatureState features,
            Instant timestamp
    ) {
    }

    public record FeatureState(
            boolean enabled,
            boolean registrationEnabled,
            boolean publicConfirmEnabled,
            boolean workerEnabled,
            boolean cloudDispatchEnabled,
            boolean adminOnly
    ) {
        static FeatureState from(RagExecutionFeatureProperties features) {
            return new FeatureState(
                    features.enabled(),
                    features.registrationEnabled(),
                    features.publicConfirmEnabled(),
                    features.workerEnabled(),
                    features.cloudDispatchEnabled(),
                    features.adminOnly()
            );
        }
    }

    public record DependencyHealthResponse(
            String status,
            String service,
            DependencySet dependencies,
            Instant timestamp
    ) {
    }

    public record DependencySet(
            DependencyState database,
            DependencyState identity,
            DependencyState cloudStorage
    ) {
    }

    public record DependencyState(
            String status,
            boolean available,
            String detail
    ) {
        static DependencyState from(DependencyHealth health) {
            return new DependencyState(
                    health.available() ? "ok" : "unavailable",
                    health.available(),
                    health.detail()
            );
        }

        static DependencyState inactive(String detail) {
            return new DependencyState("inactive", false, detail);
        }
    }
}
