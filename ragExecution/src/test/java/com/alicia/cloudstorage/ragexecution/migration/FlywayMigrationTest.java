package com.alicia.cloudstorage.ragexecution.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest {

    @Test
    void migratesAnEmptyDatabaseAndRepeatsWithoutSchemaChanges() throws Exception {
        String databaseName = "rag_execution_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(4);
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet tables = statement.executeQuery("SHOW TABLES")) {
            Set<String> names = new HashSet<>();
            while (tables.next()) {
                names.add(tables.getString(1).toLowerCase());
            }
            assertThat(names).contains(
                    "rag_execution",
                    "rag_execution_step",
                    "rag_execution_event",
                    "rag_execution_service_nonce",
                    "flyway_schema_history"
            );
            try (ResultSet columns = statement.executeQuery("""
                    SELECT step_key, depends_on_keys, output_key, required_client_fields
                    FROM rag_execution_step
                    WHERE 1 = 0
                    """)) {
                assertThat(columns.getMetaData().getColumnCount()).isEqualTo(4);
            }
            try (ResultSet constraints = statement.executeQuery("""
                    SELECT constraint_name
                    FROM information_schema.table_constraints
                    WHERE table_name = 'rag_execution_step'
                      AND constraint_name = 'uk_rag_execution_step_key'
                    """)) {
                assertThat(constraints.next()).isTrue();
            }
        }
    }
}
