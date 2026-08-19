package com.alicia.cloudstorage.api.config;

import com.alicia.cloudstorage.api.identity.HttpIdentityAdminGateway;
import com.alicia.cloudstorage.api.identity.HttpIdentityAuthGateway;
import com.alicia.cloudstorage.api.identity.HttpIdentityUserGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientConfigTest {

    @Test
    void providesRestClientBuilderForIdentityGateways() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(HttpClientConfig.class)) {
            assertThat(context.getBean(RestClient.Builder.class)).isNotNull();
        }
    }

    @Test
    void identityGatewayBeansCanBeCreatedWithHttpAndJsonInfrastructure() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of("alicia.identity-api.base-url=http://identity.test")
                    .applyTo(context);
            context.register(HttpClientConfig.class);
            context.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
            context.register(HttpIdentityAdminGateway.class, HttpIdentityAuthGateway.class, HttpIdentityUserGateway.class);

            context.refresh();

            assertThat(context.getBean(HttpIdentityAdminGateway.class)).isNotNull();
            assertThat(context.getBean(HttpIdentityAuthGateway.class)).isNotNull();
            assertThat(context.getBean(HttpIdentityUserGateway.class)).isNotNull();
        }
    }
}
