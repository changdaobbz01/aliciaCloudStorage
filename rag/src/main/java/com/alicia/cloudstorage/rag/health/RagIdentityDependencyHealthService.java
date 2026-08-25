package com.alicia.cloudstorage.rag.health;

import com.alicia.cloudstorage.rag.config.RagIdentityApiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class RagIdentityDependencyHealthService {

    private static final Logger log = LoggerFactory.getLogger(RagIdentityDependencyHealthService.class);
    private static final String OPERATION_PREFIX = "identity.";
    private static final String DEFAULT_SERVICE = "alicia-identity-api";

    private final RestClient restClient;
    private final RagDependencyTelemetry telemetry;

    @Autowired
    public RagIdentityDependencyHealthService(
            RagIdentityApiProperties properties,
            RagDependencyTelemetry telemetry
    ) {
        this(RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(identityRequestFactory(properties))
                .build(), telemetry);
    }

    RagIdentityDependencyHealthService(
            RestClient restClient,
            RagDependencyTelemetry telemetry
    ) {
        this.restClient = restClient;
        this.telemetry = telemetry;
    }

    public RagDependencyHealth check() {
        try {
            IdentityHealthPayload response = telemetry.observe("identity.health", () -> restClient.get()
                    .uri("/api/identity/health")
                    .retrieve()
                    .body(IdentityHealthPayload.class));

            if (response != null && "ok".equalsIgnoreCase(response.status())) {
                return RagDependencyHealth.available(response.service(), telemetry.snapshots(OPERATION_PREFIX));
            }
        } catch (RestClientException | IllegalArgumentException ex) {
            log.warn("RAG Identity dependency health check failed: {}", ex.getMessage());
        }

        return RagDependencyHealth.unavailable(DEFAULT_SERVICE, telemetry.snapshots(OPERATION_PREFIX));
    }

    private static SimpleClientHttpRequestFactory identityRequestFactory(RagIdentityApiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }

    private record IdentityHealthPayload(
            String status,
            String service
    ) {
    }
}
