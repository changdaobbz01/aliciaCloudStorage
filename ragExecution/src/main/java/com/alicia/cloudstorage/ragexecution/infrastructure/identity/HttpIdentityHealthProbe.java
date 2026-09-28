package com.alicia.cloudstorage.ragexecution.infrastructure.identity;

import com.alicia.cloudstorage.ragexecution.config.RagExecutionIdentityProperties;
import com.alicia.cloudstorage.ragexecution.port.DependencyHealth;
import com.alicia.cloudstorage.ragexecution.port.IdentityHealthProbe;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpIdentityHealthProbe implements IdentityHealthProbe {

    private final RestClient restClient;

    public HttpIdentityHealthProbe(RagExecutionIdentityProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public DependencyHealth check() {
        try {
            restClient.get().uri("/api/identity/health").retrieve().toBodilessEntity();
            return new DependencyHealth(true, "reachable");
        } catch (RestClientException exception) {
            return new DependencyHealth(false, "unreachable");
        }
    }
}
