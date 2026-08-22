package com.alicia.cloudstorage.identity.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class IdentitySourceBoundaryTest {

    private static final List<Path> IDENTITY_BOUNDARY_TARGETS = List.of(
            Path.of("src", "main", "java"),
            Path.of("src", "main", "resources", "db", "identity-migration")
    );
    private static final List<RestrictedCloudReference> RESTRICTED_CLOUD_REFERENCES = List.of(
            new RestrictedCloudReference("storageQuotaBytes", "cloud storage quota property"),
            new RestrictedCloudReference("homeBackgroundUrl", "cloud home background property"),
            new RestrictedCloudReference("storage_quota_bytes", "cloud storage quota column"),
            new RestrictedCloudReference("home_background_url", "cloud home background column"),
            new RestrictedCloudReference("cloud_user_profile", "cloud user profile table")
    );

    @Test
    void identitySourceDoesNotReferenceCloudOwnedProfileFields() {
        List<String> violations = IDENTITY_BOUNDARY_TARGETS.stream()
                .filter(Files::exists)
                .flatMap(path -> regularFiles(path).stream())
                .filter(IdentitySourceBoundaryTest::isTextSourceFile)
                .flatMap(path -> restrictedCloudReferences(path).stream())
                .toList();

        assertThat(violations)
                .as("Identity owns account data only. Cloud quota/background fields must stay in CloudStorageApi cloud profile code and migrations.")
                .isEmpty();
    }

    private static List<Path> regularFiles(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan " + root, ex);
        }
    }

    private static boolean isTextSourceFile(Path path) {
        String lowerCaseFileName = path.getFileName().toString().toLowerCase();
        return lowerCaseFileName.endsWith(".java")
                || lowerCaseFileName.endsWith(".properties")
                || lowerCaseFileName.endsWith(".sql");
    }

    private static List<String> restrictedCloudReferences(Path path) {
        String relativePath = Path.of("").toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
        String text = readText(path);

        return RESTRICTED_CLOUD_REFERENCES.stream()
                .filter(reference -> text.contains(reference.value()))
                .map(reference -> relativePath + " contains " + reference.description() + " (" + reference.value() + ")")
                .toList();
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }

    private record RestrictedCloudReference(String value, String description) {
    }
}
