package com.alicia.cloudstorage.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityDependencyHealthServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private IdentityDependencyHealthService identityDependencyHealthService;

    @BeforeEach
    void setUp() {
        identityDependencyHealthService = new IdentityDependencyHealthService(jdbcTemplate);
    }

    @Test
    void checkReportsAvailableWhenDatabaseAndFlywayHistoryAreReadable() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("FROM identity_flyway_schema_history"), eq(String.class)))
                .thenReturn("1");

        IdentityDependencyHealth health = identityDependencyHealthService.check();

        assertThat(health.available()).isTrue();
        assertThat(health.database().available()).isTrue();
        assertThat(health.flyway().available()).isTrue();
        assertThat(health.flyway().historyTable()).isEqualTo("identity_flyway_schema_history");
        assertThat(health.flyway().latestVersion()).isEqualTo("1");
    }

    @Test
    void checkReportsUnavailableWhenDatabaseIsUnavailable() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("db unavailable"));

        IdentityDependencyHealth health = identityDependencyHealthService.check();

        assertThat(health.available()).isFalse();
        assertThat(health.database().status()).isEqualTo("unavailable");
        assertThat(health.flyway().status()).isEqualTo("unavailable");
        verify(jdbcTemplate, never())
                .queryForObject(contains("FROM identity_flyway_schema_history"), eq(String.class));
    }

    @Test
    void checkReportsUnavailableWhenFlywayHistoryIsUnavailable() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("FROM identity_flyway_schema_history"), eq(String.class)))
                .thenThrow(new DataAccessResourceFailureException("flyway unavailable"));

        IdentityDependencyHealth health = identityDependencyHealthService.check();

        assertThat(health.available()).isFalse();
        assertThat(health.database().available()).isTrue();
        assertThat(health.flyway().available()).isFalse();
        assertThat(health.flyway().historyTable()).isEqualTo("identity_flyway_schema_history");
    }
}
