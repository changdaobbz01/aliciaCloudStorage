package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.IdentityAuditLogPageResponse;
import com.alicia.cloudstorage.identity.dto.IdentityAuditLogResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityAuditLogQueryServiceTest {

    @Mock
    private IdentityPrincipalService identityPrincipalService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private IdentityAuditLogQueryService identityAuditLogQueryService;

    @BeforeEach
    void setUp() {
        identityAuditLogQueryService = new IdentityAuditLogQueryService(identityPrincipalService, jdbcTemplate);
    }

    @Test
    void listAuditLogsRequiresAdminAndAppliesFilters() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(21L);
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<IdentityAuditLogResponse>>any(),
                any(Object[].class)
        )).thenAnswer(invocation -> {
            RowMapper<IdentityAuditLogResponse> rowMapper = invocation.getArgument(1);
            return List.of(rowMapper.mapRow(resultSet(), 0));
        });

        LocalDateTime createdFrom = LocalDateTime.of(2026, 8, 21, 0, 0);
        LocalDateTime createdTo = LocalDateTime.of(2026, 8, 21, 23, 59, 59);
        IdentityAuditLogPageResponse response = identityAuditLogQueryService.listAuditLogs(
                "Bearer admin-token",
                new IdentityAuditLogQuery(
                        "login",
                        "success",
                        1L,
                        2L,
                        "138",
                        createdFrom,
                        createdTo,
                        2,
                        10
                )
        );

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalItems()).isEqualTo(21L);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().eventType()).isEqualTo("LOGIN");
        assertThat(response.items().getFirst().actorUserId()).isEqualTo(1L);
        assertThat(response.items().getFirst().targetUserId()).isEqualTo(2L);
        assertThat(response.items().getFirst().createdAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 21, 14, 47, 25));

        verify(identityPrincipalService).requireAdminUser("Bearer admin-token");

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(countSqlCaptor.capture(), eq(Long.class), countArgsCaptor.capture());
        assertThat(countSqlCaptor.getValue())
                .contains("FROM identity_audit_log")
                .contains("event_type = ?")
                .contains("outcome = ?")
                .contains("actor_user_id = ?")
                .contains("target_user_id = ?")
                .contains("identifier LIKE ?")
                .contains("created_at >= ?")
                .contains("created_at <= ?");
        assertThat(countArgsCaptor.getValue()).containsExactly(
                "LOGIN",
                "SUCCESS",
                1L,
                2L,
                "%138%",
                Timestamp.valueOf(createdFrom),
                Timestamp.valueOf(createdTo)
        );

        ArgumentCaptor<String> querySqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> queryArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(
                querySqlCaptor.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<IdentityAuditLogResponse>>any(),
                queryArgsCaptor.capture()
        );
        assertThat(querySqlCaptor.getValue())
                .contains("SELECT id, event_type, outcome")
                .contains("ORDER BY id DESC LIMIT ? OFFSET ?");
        assertThat(queryArgsCaptor.getValue()).containsExactly(
                "LOGIN",
                "SUCCESS",
                1L,
                2L,
                "%138%",
                Timestamp.valueOf(createdFrom),
                Timestamp.valueOf(createdTo),
                10,
                10
        );
    }

    @Test
    void listAuditLogsNormalizesPaginationDefaultsAndMaximumSize() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<IdentityAuditLogResponse>>any(),
                any(Object[].class)
        )).thenReturn(List.of());

        IdentityAuditLogPageResponse response = identityAuditLogQueryService.listAuditLogs(
                "Bearer admin-token",
                new IdentityAuditLogQuery(null, null, null, null, " ", null, null, 0, 1000)
        );

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(100);
        assertThat(response.totalItems()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.items()).isEmpty();

        ArgumentCaptor<Object[]> queryArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<IdentityAuditLogResponse>>any(),
                queryArgsCaptor.capture()
        );
        assertThat(queryArgsCaptor.getValue()).containsExactly(100, 0);
    }

    @Test
    void listAuditLogsRejectsUnsupportedEventType() {
        assertThatThrownBy(() -> identityAuditLogQueryService.listAuditLogs(
                "Bearer admin-token",
                new IdentityAuditLogQuery("unknown", null, null, null, null, null, null, 1, 20)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不支持的审计事件类型。");

        verify(identityPrincipalService).requireAdminUser("Bearer admin-token");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void listAuditLogsRejectsInvalidDateRange() {
        assertThatThrownBy(() -> identityAuditLogQueryService.listAuditLogs(
                "Bearer admin-token",
                new IdentityAuditLogQuery(
                        null,
                        null,
                        null,
                        null,
                        null,
                        LocalDateTime.of(2026, 8, 22, 0, 0),
                        LocalDateTime.of(2026, 8, 21, 0, 0),
                        1,
                        20
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("审计日志开始时间不能晚于结束时间。");

        verify(identityPrincipalService).requireAdminUser("Bearer admin-token");
        verifyNoInteractions(jdbcTemplate);
    }

    private ResultSet resultSet() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(3L);
        when(resultSet.getString("event_type")).thenReturn("LOGIN");
        when(resultSet.getString("outcome")).thenReturn("SUCCESS");
        when(resultSet.getLong("actor_user_id")).thenReturn(1L);
        when(resultSet.getLong("target_user_id")).thenReturn(2L);
        when(resultSet.wasNull()).thenReturn(false, false);
        when(resultSet.getString("identifier")).thenReturn("13800000000");
        when(resultSet.getString("detail")).thenReturn(null);
        when(resultSet.getTimestamp("created_at"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 8, 21, 14, 47, 25)));
        return resultSet;
    }
}
