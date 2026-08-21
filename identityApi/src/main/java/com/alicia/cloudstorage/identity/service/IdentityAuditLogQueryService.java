package com.alicia.cloudstorage.identity.service;

import com.alicia.cloudstorage.identity.dto.IdentityAuditLogPageResponse;
import com.alicia.cloudstorage.identity.dto.IdentityAuditLogResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class IdentityAuditLogQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String SELECT_COLUMNS = """
            SELECT id, event_type, outcome, actor_user_id, target_user_id, identifier, detail, created_at
            FROM identity_audit_log
            """;

    private final IdentityPrincipalService identityPrincipalService;
    private final JdbcTemplate jdbcTemplate;

    public IdentityAuditLogQueryService(
            IdentityPrincipalService identityPrincipalService,
            JdbcTemplate jdbcTemplate
    ) {
        this.identityPrincipalService = identityPrincipalService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public IdentityAuditLogPageResponse listAuditLogs(
            String authorizationHeader,
            IdentityAuditLogQuery query
    ) {
        identityPrincipalService.requireAdminUser(authorizationHeader);

        int page = sanitizePage(query.page());
        int size = sanitizeSize(query.size());
        List<Object> parameters = new ArrayList<>();
        String whereClause = buildWhereClause(query, parameters);

        Long totalItems = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_audit_log" + whereClause,
                Long.class,
                parameters.toArray()
        );
        long safeTotalItems = totalItems == null ? 0L : totalItems;

        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((page - 1) * size);

        List<IdentityAuditLogResponse> items = jdbcTemplate.query(
                SELECT_COLUMNS + whereClause + " ORDER BY id DESC LIMIT ? OFFSET ?",
                this::mapRow,
                pageParameters.toArray()
        );

        return new IdentityAuditLogPageResponse(
                items,
                page,
                size,
                safeTotalItems,
                calculateTotalPages(safeTotalItems, size)
        );
    }

    private String buildWhereClause(IdentityAuditLogQuery query, List<Object> parameters) {
        if (query.createdFrom() != null && query.createdTo() != null && query.createdFrom().isAfter(query.createdTo())) {
            throw new IllegalArgumentException("审计日志开始时间不能晚于结束时间。");
        }

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");

        String eventType = normalizeEventType(query.eventType());
        if (eventType != null) {
            where.append(" AND event_type = ?");
            parameters.add(eventType);
        }

        String outcome = normalizeOutcome(query.outcome());
        if (outcome != null) {
            where.append(" AND outcome = ?");
            parameters.add(outcome);
        }

        if (query.actorUserId() != null) {
            where.append(" AND actor_user_id = ?");
            parameters.add(query.actorUserId());
        }

        if (query.targetUserId() != null) {
            where.append(" AND target_user_id = ?");
            parameters.add(query.targetUserId());
        }

        String identifier = normalizeText(query.identifier());
        if (identifier != null) {
            where.append(" AND identifier LIKE ?");
            parameters.add("%" + identifier + "%");
        }

        if (query.createdFrom() != null) {
            where.append(" AND created_at >= ?");
            parameters.add(Timestamp.valueOf(query.createdFrom()));
        }

        if (query.createdTo() != null) {
            where.append(" AND created_at <= ?");
            parameters.add(Timestamp.valueOf(query.createdTo()));
        }

        return where.toString();
    }

    private IdentityAuditLogResponse mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        return new IdentityAuditLogResponse(
                resultSet.getLong("id"),
                resultSet.getString("event_type"),
                resultSet.getString("outcome"),
                nullableLong(resultSet, "actor_user_id"),
                nullableLong(resultSet, "target_user_id"),
                resultSet.getString("identifier"),
                resultSet.getString("detail"),
                createdAt == null ? null : createdAt.toLocalDateTime()
        );
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private int sanitizePage(Integer page) {
        if (page == null || page < 1) {
            return DEFAULT_PAGE;
        }

        return page;
    }

    private int sanitizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }

        return Math.min(size, MAX_SIZE);
    }

    private int calculateTotalPages(long totalItems, int size) {
        if (totalItems == 0L) {
            return 0;
        }

        return (int) Math.ceil(totalItems / (double) size);
    }

    private String normalizeEventType(String value) {
        return normalizeEnum(value, IdentityAuditEventType.class, "不支持的审计事件类型。");
    }

    private String normalizeOutcome(String value) {
        return normalizeEnum(value, IdentityAuditOutcome.class, "不支持的审计结果。");
    }

    private <T extends Enum<T>> String normalizeEnum(String value, Class<T> enumType, String errorMessage) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }

        try {
            return Enum.valueOf(enumType, normalized.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
