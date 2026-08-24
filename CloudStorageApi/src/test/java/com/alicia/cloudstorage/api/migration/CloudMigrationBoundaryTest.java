package com.alicia.cloudstorage.api.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CloudMigrationBoundaryTest {

    private static final Path CLOUD_MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");
    private static final Set<String> LEGACY_IDENTITY_MIGRATIONS = Set.of(
            "V1__init_schema.sql",
            "V2__add_trash_metadata.sql",
            "V4__add_multipart_upload_sessions.sql",
            "V5__add_user_storage_quota.sql",
            "V6__add_user_home_background.sql",
            "V7__add_user_token_version.sql",
            "V8__add_share_links.sql",
            "V9__add_app_package_releases.sql",
            "V11__add_email_registration.sql",
            "V12__create_cloud_user_profile.sql",
            "V13__create_identity_audit_log.sql",
            "V14__create_identity_refresh_token.sql",
            "V15__drop_legacy_cloud_profile_columns_from_sys_user.sql"
    );
    private static final List<RestrictedIdentityPattern> RESTRICTED_IDENTITY_PATTERNS = List.of(
            new RestrictedIdentityPattern("create table if not exists sys_user", "sys_user table creation"),
            new RestrictedIdentityPattern("create table sys_user", "sys_user table creation"),
            new RestrictedIdentityPattern("alter table sys_user", "sys_user table alteration"),
            new RestrictedIdentityPattern("references sys_user", "sys_user foreign key reference"),
            new RestrictedIdentityPattern("'sys_user'", "legacy identity table literal"),
            new RestrictedIdentityPattern("create table if not exists identity_user", "identity_user table creation"),
            new RestrictedIdentityPattern("create table identity_user", "identity_user table creation"),
            new RestrictedIdentityPattern("alter table identity_user", "identity_user table alteration"),
            new RestrictedIdentityPattern("references identity_user", "identity_user foreign key reference"),
            new RestrictedIdentityPattern("rename table sys_user to identity_user", "identity table rename"),
            new RestrictedIdentityPattern("'identity_user'", "identity table literal"),
            new RestrictedIdentityPattern("email_verification_code", "email verification table"),
            new RestrictedIdentityPattern("identity_audit_log", "identity audit table"),
            new RestrictedIdentityPattern("identity_refresh_token", "identity refresh token table"),
            new RestrictedIdentityPattern("token_version", "identity token version column"),
            new RestrictedIdentityPattern("email_verified_at", "identity email verification column"),
            new RestrictedIdentityPattern("uk_sys_user_email", "legacy identity email unique index"),
            new RestrictedIdentityPattern("uk_identity_user_email", "identity email unique index")
    );

    @Test
    void cloudStorageMigrationsDoNotAddNewIdentityOwnedSchema() throws IOException {
        try (Stream<Path> migrationFiles = Files.list(CLOUD_MIGRATION_DIR)) {
            List<String> violations = migrationFiles
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .filter(path -> !LEGACY_IDENTITY_MIGRATIONS.contains(path.getFileName().toString()))
                    .flatMap(path -> restrictedIdentityPatterns(path).stream())
                    .toList();

            assertThat(violations)
                    .as("New identity-owned schema changes belong in identityApi/src/main/resources/db/identity-migration; CloudStorageApi keeps only legacy applied identity migrations.")
                    .isEmpty();
        }
    }

    private static List<String> restrictedIdentityPatterns(Path path) {
        String fileName = path.getFileName().toString();
        String sql = normalizeSql(readSql(path));

        return RESTRICTED_IDENTITY_PATTERNS.stream()
                .filter(pattern -> sql.contains(pattern.sqlFragment()))
                .map(pattern -> fileName + " contains " + pattern.description())
                .toList();
    }

    private static String readSql(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read migration file " + path, ex);
        }
    }

    private static String normalizeSql(String sql) {
        return sql
                .toLowerCase()
                .replace('`', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record RestrictedIdentityPattern(String sqlFragment, String description) {
    }
}
