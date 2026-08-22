package com.alicia.cloudstorage.identity.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityMigrationBoundaryTest {

    private static final Path IDENTITY_MIGRATION_DIR =
            Path.of("src", "main", "resources", "db", "identity-migration");
    private static final List<RestrictedCloudPattern> RESTRICTED_CLOUD_PATTERNS = List.of(
            new RestrictedCloudPattern("storage_node", "cloud storage node table"),
            new RestrictedCloudPattern("cloud_user_profile", "cloud user profile table"),
            new RestrictedCloudPattern("multipart_upload_session", "multipart upload session table"),
            new RestrictedCloudPattern("multipart_upload_part", "multipart upload part table"),
            new RestrictedCloudPattern("share_link", "cloud share link table"),
            new RestrictedCloudPattern("share_link_item", "cloud share link item table"),
            new RestrictedCloudPattern("app_package_release", "app package release table"),
            new RestrictedCloudPattern("storage_quota_bytes", "cloud storage quota field"),
            new RestrictedCloudPattern("home_background_url", "cloud home background field"),
            new RestrictedCloudPattern("storage_path", "cloud object storage path field")
    );

    @Test
    void identityMigrationsDoNotAddCloudOwnedSchema() throws IOException {
        try (Stream<Path> migrationFiles = Files.list(IDENTITY_MIGRATION_DIR)) {
            List<String> violations = migrationFiles
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .flatMap(path -> restrictedCloudPatterns(path).stream())
                    .toList();

            assertThat(violations)
                    .as("Cloud-owned schema changes belong in CloudStorageApi/src/main/resources/db/migration; identityApi migrations must stay inside the shared identity boundary.")
                    .isEmpty();
        }
    }

    private static List<String> restrictedCloudPatterns(Path path) {
        String fileName = path.getFileName().toString();
        String sql = normalizeSql(readSql(path));

        return RESTRICTED_CLOUD_PATTERNS.stream()
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

    private record RestrictedCloudPattern(String sqlFragment, String description) {
    }
}
