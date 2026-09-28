package com.alicia.cloudstorage.ragexecution.infrastructure.persistence;

import com.alicia.cloudstorage.ragexecution.port.DatabaseHealthProbe;
import com.alicia.cloudstorage.ragexecution.port.DependencyHealth;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseDependencyHealthProbe implements DatabaseHealthProbe {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseDependencyHealthProbe(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DependencyHealth check() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (Integer.valueOf(1).equals(value)) {
                return new DependencyHealth(true, "reachable");
            }
            return new DependencyHealth(false, "unexpected-probe-result");
        } catch (RuntimeException exception) {
            return new DependencyHealth(false, "probe-failed");
        }
    }
}
