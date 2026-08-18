package com.alicia.cloudstorage.api.config;

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
}
