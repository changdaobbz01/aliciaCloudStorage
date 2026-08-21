package com.alicia.cloudstorage.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class IdentityAuditLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityAuditLogService.class);
    private static final int MAX_IDENTIFIER_LENGTH = 255;
    private static final int MAX_DETAIL_LENGTH = 1000;
    private static final String INSERT_AUDIT_SQL = """
            INSERT INTO identity_audit_log (
                event_type,
                outcome,
                actor_user_id,
                target_user_id,
                identifier,
                detail,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public IdentityAuditLogService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            IdentityAuditEventType eventType,
            IdentityAuditOutcome outcome,
            Long actorUserId,
            Long targetUserId,
            String identifier,
            String detail
    ) {
        try {
            jdbcTemplate.update(
                    INSERT_AUDIT_SQL,
                    Objects.requireNonNull(eventType, "eventType").name(),
                    Objects.requireNonNull(outcome, "outcome").name(),
                    actorUserId,
                    targetUserId,
                    normalize(identifier, MAX_IDENTIFIER_LENGTH),
                    normalize(detail, MAX_DETAIL_LENGTH),
                    Timestamp.valueOf(LocalDateTime.now(clock))
            );
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to persist identity audit log for event {}: {}", eventType, ex.getMessage());
        }
    }

    private String normalize(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(0, maxLength);
    }
}
