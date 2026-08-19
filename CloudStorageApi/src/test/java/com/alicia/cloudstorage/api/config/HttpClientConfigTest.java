package com.alicia.cloudstorage.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientConfigTest {

    @Test
    void providesRestClientBuilderForIdentityGateways() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(HttpClientConfig.class)) {
            assertThat(context.getBean(RestClient.Builder.class)).isNotNull();
        }
    }
}
