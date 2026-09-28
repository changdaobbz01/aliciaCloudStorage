package com.alicia.cloudstorage.ragexecution.infrastructure.cloud;

import com.alicia.cloudstorage.ragexecution.config.RagExecutionCloudProperties;
import com.alicia.cloudstorage.ragexecution.port.CloudHealthProbe;
import com.alicia.cloudstorage.ragexecution.port.DependencyHealth;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCloudHealthProbe implements CloudHealthProbe {

    private final RestClient restClient;

    public HttpCloudHealthProbe(RagExecutionCloudProperties properties) {
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
            restClient.get().uri("/api/health").retrieve().toBodilessEntity();
            return new DependencyHealth(true, "reachable");
        } catch (RuntimeException exception) {
            return new DependencyHealth(false, "unreachable");
        }
    }
}
