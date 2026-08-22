package com.alicia.cloudstorage.api.identity;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityApiClientPropertiesTest {

    @Test
    void trimsBaseUrlAndRemovesTrailingSlashes() {
        IdentityApiClientProperties properties = new IdentityApiClientProperties(
                " http://identity.test/// ",
                2000L,
                5000L
        );

        assertThat(properties.baseUrl()).isEqualTo("http://identity.test");
    }

    @Test
    void normalizesTimeoutsToAtLeastOneMillisecond() {
        IdentityApiClientProperties properties = new IdentityApiClientProperties(
                "http://identity.test",
                0L,
                -5L
        );

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(1L));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofMillis(1L));
    }

    @Test
    void rejectsBlankBaseUrl() {
        assertThatThrownBy(() -> new IdentityApiClientProperties(" ", 2000L, 5000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Identity API base URL must be configured.");
    }
}
