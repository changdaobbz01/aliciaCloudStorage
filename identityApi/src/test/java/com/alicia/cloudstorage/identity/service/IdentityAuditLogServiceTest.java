package com.alicia.cloudstorage.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdentityAuditLogServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private IdentityAuditLogService identityAuditLogService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-21T02:30:00Z"),
                ZoneId.of("Asia/Shanghai")
        );
        identityAuditLogService = new IdentityAuditLogService(jdbcTemplate, clock);
    }

    @Test
    void recordPersistsNormalizedAuditFields() {
        String longDetail = "x".repeat(1200);

        identityAuditLogService.record(
                IdentityAuditEventType.LOGIN,
                IdentityAuditOutcome.FAILURE,
                1L,
                2L,
                " user@example.com ",
                longDetail
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("INSERT INTO identity_audit_log");
        assertThat(argsCaptor.getValue()).containsExactly(
                "LOGIN",
                "FAILURE",
                1L,
                2L,
                "user@example.com",
                "x".repeat(1000),
                Timestamp.valueOf(LocalDateTime.of(2026, 8, 21, 10, 30))
        );
    }

    @Test
    void recordSwallowsPersistenceFailure() {
        doThrow(new DataAccessResourceFailureException("db unavailable"))
                .when(jdbcTemplate)
                .update(anyString(), any(Object[].class));

        assertThatCode(() -> identityAuditLogService.record(
                IdentityAuditEventType.LOGOUT,
                IdentityAuditOutcome.SUCCESS,
                1L,
                1L,
                "user@example.com",
                null
        )).doesNotThrowAnyException();
    }
}
