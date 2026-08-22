package com.alicia.cloudstorage.api.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityRouteBoundaryTest {

    private static final Set<String> EXCLUDED_DEPLOY_SCRIPTS = Set.of(
            "check-identity-route-boundary.sh",
            "verify-identity-cloud-routes.sh"
    );
    private static final List<Path> SOURCE_BOUNDARY_TARGETS = List.of(
            Path.of("webApp", "src"),
            Path.of("phoneApp", "app", "src", "main"),
            Path.of("phoneAppAdd", "app", "src", "main"),
            Path.of("CloudStorageApi", "src", "main"),
            Path.of("identityApi", "src", "main"),
            Path.of("rag", "src", "main"),
            Path.of("deploy")
    );
    private static final List<LegacyRoutePattern> LEGACY_ROUTE_PATTERNS = List.of(
            new LegacyRoutePattern("/api/auth", "legacy cloud auth route"),
            new LegacyRoutePattern("api/auth", "legacy mobile cloud auth route"),
            new LegacyRoutePattern("/api/admin/users", "legacy cloud admin users route"),
            new LegacyRoutePattern("api/admin/users", "legacy mobile cloud admin users route")
    );

    @Test
    void sourceAndDeployBoundaryDoNotReferenceLegacyIdentityRoutes() throws IOException {
        Path repositoryRoot = repositoryRoot();

        List<String> violations = SOURCE_BOUNDARY_TARGETS.stream()
                .map(repositoryRoot::resolve)
                .filter(Files::exists)
                .flatMap(path -> regularFiles(path).stream())
                .filter(IdentityRouteBoundaryTest::shouldScan)
                .flatMap(path -> legacyRouteReferences(repositoryRoot, path).stream())
                .toList();

        assertThat(violations)
                .as("""
                        Use /api/identity/** for identity and /api/admin/cloud-users or /api/cloud-profile/**
                        for cloud-owned user data. Keep old route checks only in
                        deploy/scripts/verify-identity-cloud-routes.sh.
                        """)
                .isEmpty();
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (current.getFileName() != null && "CloudStorageApi".equals(current.getFileName().toString())) {
            return current.getParent();
        }

        return current;
    }

    private static List<Path> regularFiles(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan " + root, ex);
        }
    }

    private static boolean shouldScan(Path path) {
        String normalizedPath = path.toString().replace('\\', '/');
        if (normalizedPath.contains("/deploy/generated/")
                || normalizedPath.contains("/dist/")
                || normalizedPath.contains("/target/")) {
            return false;
        }

        return !EXCLUDED_DEPLOY_SCRIPTS.contains(path.getFileName().toString())
                && isTextSourceFile(path);
    }

    private static boolean isTextSourceFile(Path path) {
        String fileName = path.getFileName().toString();
        if (Set.of("Dockerfile", ".env.example").contains(fileName)) {
            return true;
        }

        String lowerCaseFileName = fileName.toLowerCase();
        return lowerCaseFileName.endsWith(".conf")
                || lowerCaseFileName.endsWith(".css")
                || lowerCaseFileName.endsWith(".html")
                || lowerCaseFileName.endsWith(".java")
                || lowerCaseFileName.endsWith(".js")
                || lowerCaseFileName.endsWith(".json")
                || lowerCaseFileName.endsWith(".kt")
                || lowerCaseFileName.endsWith(".md")
                || lowerCaseFileName.endsWith(".properties")
                || lowerCaseFileName.endsWith(".ps1")
                || lowerCaseFileName.endsWith(".sh")
                || lowerCaseFileName.endsWith(".ts")
                || lowerCaseFileName.endsWith(".tsx")
                || lowerCaseFileName.endsWith(".xml")
                || lowerCaseFileName.endsWith(".yaml")
                || lowerCaseFileName.endsWith(".yml");
    }

    private static List<String> legacyRouteReferences(Path repositoryRoot, Path path) {
        String text = readText(path);
        String relativePath = repositoryRoot.relativize(path).toString().replace('\\', '/');

        return LEGACY_ROUTE_PATTERNS.stream()
                .filter(pattern -> text.contains(pattern.value()))
                .map(pattern -> relativePath + " contains " + pattern.description() + " (" + pattern.value() + ")")
                .toList();
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }

    private record LegacyRoutePattern(String value, String description) {
    }
}
