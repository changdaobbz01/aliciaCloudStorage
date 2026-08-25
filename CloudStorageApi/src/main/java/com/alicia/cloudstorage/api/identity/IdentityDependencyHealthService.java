package com.alicia.cloudstorage.api.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class IdentityDependencyHealthService {

    private static final Logger log = LoggerFactory.getLogger(IdentityDependencyHealthService.class);

    private final RestClient restClient;
    private final IdentityGatewayTelemetry telemetry;
    private final IdentityCurrentUserCache currentUserCache;

    public IdentityDependencyHealthService(
            @Qualifier("identityRestClient") RestClient restClient,
            IdentityGatewayTelemetry telemetry,
            IdentityCurrentUserCache currentUserCache
    ) {
        this.restClient = restClient;
        this.telemetry = telemetry;
        this.currentUserCache = currentUserCache;
    }

    public IdentityDependencyHealth check() {
        try {
            IdentityHealthPayload response = restClient.get()
                    .uri("/api/identity/health")
                    .retrieve()
                    .body(IdentityHealthPayload.class);

            if (response != null && "ok".equalsIgnoreCase(response.status())) {
                return IdentityDependencyHealth.available(
                        response.service(),
                        currentUserCache.snapshot(),
                        telemetry.snapshots()
                );
            }
        } catch (RestClientException | IllegalArgumentException ex) {
            log.warn("Identity dependency health check failed: {}", ex.getMessage());
        }

        return IdentityDependencyHealth.unavailable(currentUserCache.snapshot(), telemetry.snapshots());
    }

    private record IdentityHealthPayload(
            String status,
            String service
    ) {
    }
}
