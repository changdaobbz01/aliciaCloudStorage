package com.alicia.cloudstorage.ragexecution.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionRolloutScriptContractTest {

    private static final Path DEPLOY_SCRIPTS = Path.of("..", "deploy", "scripts");

    @Test
    void rolloutStagesRemainExplicitAndProductionApplyRemainsOptIn() throws IOException {
        String common = read(DEPLOY_SCRIPTS.resolve("lib/rag-execution-production-common.sh"));
        String prepare = read(DEPLOY_SCRIPTS.resolve("prepare-rag-execution-production-env.sh"));
        String update = read(DEPLOY_SCRIPTS.resolve("update-rag-execution-production.sh"));
        String verify = read(DEPLOY_SCRIPTS.resolve("verify-rag-execution-production.sh"));

        assertThat(common).contains(
                "foundation|shadow|admin-single",
                "ALICIA_RAG_EXECUTION_ADMIN_ONLY=true",
                "ALICIA_RAG_EXECUTION_ALLOWED_ACTIONS=$action",
                "rag_execution_require_boolean",
                "rag_execution_require_positive_integer",
                "rag_execution_require_non_negative_integer"
        );
        assertThat(common).doesNotContain("NODE_BATCH_TRASH|NODE_BATCH_MOVE|NODE_BATCH_RENAME|UPLOAD_FILES)");
        assertThat(prepare).contains("chmod 600 \"$CANDIDATE_FILE\"");
        assertThat(update).contains(
                "APPLY=false",
                "--apply)",
                "ALICIA_RAG_EXECUTION_BACKUP_BEFORE_UPDATE:-true",
                "ALICIA_RAG_EXECUTION_STOP_ON_VERIFY_FAILURE:-true",
                "rag_execution_require_boolean ALICIA_RAG_EXECUTION_BACKUP_BEFORE_UPDATE",
                "rag_execution_require_boolean ALICIA_RAG_EXECUTION_STOP_ON_VERIFY_FAILURE",
                "compose stop rag-execution"
        );
        assertThat(verify).contains(
                "--preflight",
                "expect_http_404",
                "rag_execution_require_positive_integer ALICIA_RAG_EXECUTION_VERIFY_ATTEMPTS",
                "rag_execution_require_non_negative_integer ALICIA_RAG_EXECUTION_VERIFY_MAX_RESTARTS",
                "restart count"
        );
    }

    @Test
    void productionBackupIncludesTheIndependentExecutionDatabaseWhenPresent() throws IOException {
        String backup = read(DEPLOY_SCRIPTS.resolve("backup-production-data.sh"));
        String validation = read(DEPLOY_SCRIPTS.resolve("validate-production-backup.sh"));

        assertThat(backup).contains(
                "ALICIA_RAG_EXECUTION_MYSQL_DATABASE",
                "rag-execution-$RAG_EXECUTION_DATABASE.sql.gz",
                "rag_execution_database_exists=%s"
        );
        assertThat(validation).contains(
                "rag_execution_database_exists",
                "Missing RAG execution database dump."
        );
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
