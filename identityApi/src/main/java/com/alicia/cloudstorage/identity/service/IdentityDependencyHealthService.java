package com.alicia.cloudstorage.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdentityDependencyHealthService {

    private static final Logger log = LoggerFactory.getLogger(IdentityDependencyHealthService.class);

    private static final String DATABASE_HEALTH_SQL = "SELECT 1";
    private static final String FLYWAY_HEALTH_SQL = """
            SELECT version
            FROM identity_flyway_schema_history
            WHERE success = 1
            ORDER BY installed_rank DESC
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public IdentityDependencyHealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public IdentityDependencyHealth check() {
        IdentityDependencyCheck database = checkDatabase();
        IdentityFlywayDependencyCheck flyway = database.available()
                ? checkFlyway()
                : IdentityFlywayDependencyCheck.unavailable();

        return IdentityDependencyHealth.of(database, flyway);
    }

    private IdentityDependencyCheck checkDatabase() {
        try {
            Integer result = jdbcTemplate.queryForObject(DATABASE_HEALTH_SQL, Integer.class);
            if (result != null && result == 1) {
                return IdentityDependencyCheck.ok();
            }
        } catch (DataAccessException ex) {
            log.warn("Identity database health check failed: {}", ex.getMessage());
        }

        return IdentityDependencyCheck.unavailable();
    }

    private IdentityFlywayDependencyCheck checkFlyway() {
        try {
            String latestVersion = jdbcTemplate.queryForObject(FLYWAY_HEALTH_SQL, String.class);
            return IdentityFlywayDependencyCheck.ok(latestVersion);
        } catch (DataAccessException ex) {
            log.warn("Identity Flyway health check failed: {}", ex.getMessage());
        }

        return IdentityFlywayDependencyCheck.unavailable();
    }
}
