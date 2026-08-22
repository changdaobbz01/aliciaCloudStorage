package com.alicia.cloudstorage.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder(
            @Value("${alicia.identity-api.connect-timeout-ms:2000}") long identityConnectTimeoutMs,
            @Value("${alicia.identity-api.read-timeout-ms:5000}") long identityReadTimeoutMs
    ) {
        return RestClient.builder()
                .requestFactory(identityRequestFactory(identityConnectTimeoutMs, identityReadTimeoutMs));
    }

    static SimpleClientHttpRequestFactory identityRequestFactory(long connectTimeoutMs, long readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(normalizeTimeoutMs(connectTimeoutMs)));
        requestFactory.setReadTimeout(Duration.ofMillis(normalizeTimeoutMs(readTimeoutMs)));
        return requestFactory;
    }

    private static long normalizeTimeoutMs(long timeoutMs) {
        return Math.max(1L, timeoutMs);
    }
}
