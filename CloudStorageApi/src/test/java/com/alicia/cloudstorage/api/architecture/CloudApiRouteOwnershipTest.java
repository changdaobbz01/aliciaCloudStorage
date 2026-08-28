package com.alicia.cloudstorage.api.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CloudApiRouteOwnershipTest {

    private static final Path CONTROLLER_SOURCE_ROOT = Path.of(
            "src", "main", "java", "com", "alicia", "cloudstorage", "api", "controller"
    );
    private static final List<String> ALLOWED_API_PREFIXES = List.of(
            "/api/health",
            "/api/cloud-profile",
            "/api/storage",
            "/api/share-links",
            "/api/public/share-links",
            "/api/admin/cloud-users",
            "/api/admin/cloud-operations",
            "/api/admin/app-package",
            "/api/app-package"
    );
    private static final List<String> FORBIDDEN_IDENTITY_PREFIXES = List.of(
            "/api/identity",
            "/api/auth",
            "/api/admin/users"
    );
    private static final Pattern MAPPING_ANNOTATION = Pattern.compile(
            "@(?:RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\\s*\\(([^)]*)\\)",
            Pattern.DOTALL
    );
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");

    @Test
    void cloudControllersOnlyExposeCloudOwnedApiRoutes() {
        Path controllerRoot = controllerSourceRoot();

        List<String> violations = regularJavaFiles(controllerRoot)
                .stream()
                .flatMap(path -> apiRouteMappings(controllerRoot, path).stream())
                .filter(route -> !matchesAnyPrefix(route.value(), ALLOWED_API_PREFIXES))
                .map(route -> route.location() + " exposes " + route.value())
                .toList();

        assertThat(violations)
                .as("CloudStorageApi controllers must keep API routes under cloud-owned prefixes: %s", ALLOWED_API_PREFIXES)
                .isEmpty();
    }

    @Test
    void cloudControllersDoNotExposeIdentityApiRoutes() {
        Path controllerRoot = controllerSourceRoot();

        List<String> violations = regularJavaFiles(controllerRoot)
                .stream()
                .flatMap(path -> apiRouteMappings(controllerRoot, path).stream())
                .filter(route -> matchesAnyPrefix(route.value(), FORBIDDEN_IDENTITY_PREFIXES))
                .map(route -> route.location() + " claims identity-owned route " + route.value())
                .toList();

        assertThat(violations)
                .as("Identity-owned routes belong in identityApi, not CloudStorageApi.")
                .isEmpty();
    }

    private static Path controllerSourceRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path moduleRelative = current.resolve(CONTROLLER_SOURCE_ROOT);
        if (Files.exists(moduleRelative)) {
            return moduleRelative;
        }

        Path repositoryRelative = current.resolve("CloudStorageApi").resolve(CONTROLLER_SOURCE_ROOT);
        if (Files.exists(repositoryRelative)) {
            return repositoryRelative;
        }

        throw new IllegalStateException("Unable to locate CloudStorageApi controller source root from " + current);
    }

    private static List<Path> regularJavaFiles(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan " + root, ex);
        }
    }

    private static List<ApiRouteMapping> apiRouteMappings(Path controllerRoot, Path path) {
        String text = readText(path);
        String relativePath = controllerRoot.relativize(path).toString().replace('\\', '/');

        return MAPPING_ANNOTATION.matcher(text)
                .results()
                .flatMap(annotation -> routeLiterals(annotation.group(1)).stream()
                        .filter(CloudApiRouteOwnershipTest::isApiRouteLiteral)
                        .map(CloudApiRouteOwnershipTest::normalizeApiRoute)
                        .map(route -> new ApiRouteMapping(relativePath, lineNumber(text, annotation.start()), route)))
                .toList();
    }

    private static List<String> routeLiterals(String annotationArguments) {
        Matcher matcher = STRING_LITERAL.matcher(annotationArguments);
        return matcher.results()
                .map(match -> match.group(1))
                .toList();
    }

    private static boolean isApiRouteLiteral(String value) {
        return value.equals("api")
                || value.equals("/api")
                || value.startsWith("api/")
                || value.startsWith("/api/");
    }

    private static String normalizeApiRoute(String value) {
        return value.startsWith("/") ? value : "/" + value;
    }

    private static boolean matchesAnyPrefix(String route, List<String> prefixes) {
        return prefixes.stream().anyMatch(prefix -> route.equals(prefix) || route.startsWith(prefix + "/"));
    }

    private static int lineNumber(String text, int index) {
        return 1 + (int) text.substring(0, index)
                .chars()
                .filter(character -> character == '\n')
                .count();
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }

    private record ApiRouteMapping(String sourcePath, int lineNumber, String value) {

        String location() {
            return sourcePath + ":" + lineNumber;
        }
    }
}
