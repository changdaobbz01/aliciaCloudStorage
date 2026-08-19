package com.alicia.cloudstorage.api.config;

import com.alicia.cloudstorage.api.identity.IdentityServiceUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void routeNotFoundReturnsNotFoundPayload() {
        var response = handler.handleRouteNotFound(new RuntimeException("No static resource."));

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.error()).isEqualTo("请求的资源不存在。");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void identityUnavailableReturnsServiceUnavailablePayload() {
        var response = handler.handleIdentityServiceUnavailable(
                new IdentityServiceUnavailableException("身份服务暂不可用。")
        );

        assertThat(response.status()).isEqualTo(503);
        assertThat(response.error()).isEqualTo("身份服务暂不可用。");
        assertThat(response.timestamp()).isNotNull();
    }
}
