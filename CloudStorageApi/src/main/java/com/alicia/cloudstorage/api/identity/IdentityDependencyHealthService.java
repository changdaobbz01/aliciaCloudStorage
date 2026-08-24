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

    public IdentityDependencyHealthService(
            @Qualifier("identityRestClient") RestClient restClient,
            IdentityGatewayTelemetry telemetry
    ) {
        this.restClient = restClient;
        this.telemetry = telemetry;
    }

    public IdentityDependencyHealth check() {
        try {
            IdentityHealthPayload response = restClient.get()
                    .uri("/api/identity/health")
                    .retrieve()
                    .body(IdentityHealthPayload.class);

            if (response != null && "ok".equalsIgnoreCase(response.status())) {
                return IdentityDependencyHealth.available(response.service(), telemetry.snapshots());
            }
        } catch (RestClientException | IllegalArgumentException ex) {
            log.warn("Identity dependency health check failed: {}", ex.getMessage());
        }

        return IdentityDependencyHealth.unavailable(telemetry.snapshots());
    }

    private record IdentityHealthPayload(
            String status,
            String service
    ) {
    }
}
